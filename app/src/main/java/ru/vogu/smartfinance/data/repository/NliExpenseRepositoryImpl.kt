package ru.vogu.smartfinance.data.repository

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.DefaultKeywordCategories
import ru.vogu.smartfinance.core.common.NetworkMonitor
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.common.normalizeAsAliasKeyword
import ru.vogu.smartfinance.core.common.stem
import ru.vogu.smartfinance.core.common.tokenizeWords
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.network.GeminiClient
import ru.vogu.smartfinance.core.security.PiiFilter
import ru.vogu.smartfinance.data.mapper.toDomain
import ru.vogu.smartfinance.data.parser.MultiExpenseSplitter
import ru.vogu.smartfinance.data.parser.RegexLocalParser
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.ParsedExpense
import ru.vogu.smartfinance.domain.repository.NliExpenseRepository
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import javax.inject.Inject

/** Кириллические варианты популярных брендов, у которых [DefaultKeywordCategories] знает только латиницу. */
private val BRAND_ALIASES: Map<String, String> = mapOf(
    "нетфликс" to "netflix",
    "спотифай" to "spotify"
)

/** Минимальная длина слова, для которого имеет смысл сравнивать основу — короче уже сама основа. */
private const val MIN_STEM_WORD_LENGTH = 4

/**
 * Оркестрирует полный 3-уровневый каскад — CLAUDE.md §3. Это единственное место, где
 * решение "звонить ли в LLM" принимается: сначала PII-фильтр (всегда), затем Tier 1
 * (regex), и только при его неудаче — Tier 3 (LLM), при этом категория для результата
 * ЛЮБОГО уровня всегда пытается дополнительно уточниться через Tier 2 (semantic cache) —
 * см. комментарий у [resolveCategoryViaCache].
 */
class NliExpenseRepositoryImpl @Inject constructor(
    private val piiFilter: PiiFilter,
    private val regexLocalParser: RegexLocalParser,
    private val multiExpenseSplitter: MultiExpenseSplitter,
    private val semanticAliasRepository: SemanticAliasRepository,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val geminiClient: GeminiClient,
    private val networkMonitor: NetworkMonitor
) : NliExpenseRepository {

    override suspend fun parseUserInput(rawInputText: String): Resource<List<ParsedExpense>> {
        if (rawInputText.isBlank()) {
            return Resource.Error("Пустое сообщение — нечего распознавать.")
        }

        val piiResult = piiFilter.sanitize(rawInputText)
        val sanitizedText = piiResult.sanitizedText

        // Tier 1.5: одно сообщение — несколько операций ("такси 450, кофе 200"), 0 токенов.
        // Проверяется раньше одиночного Tier 1, но не мешает ему: на тексте без разделителей
        // splitter сразу возвращает null (см. MultiExpenseSplitter).
        val multiResult = multiExpenseSplitter.trySplit(sanitizedText)
        if (multiResult != null) {
            return Resource.Success(multiResult.map { resolveCategory(it) })
        }

        // Tier 1: локальный regex, 0 токенов.
        val tier1Result = regexLocalParser.tryParse(sanitizedText)
        if (tier1Result != null) {
            return Resource.Success(listOf(resolveCategory(tier1Result)))
        }

        // Синтаксис сложнее, чем "[Сумма] [Товар]" — Tier 1 не справился. Семантический кэш
        // (Tier 2) сам по себе не умеет извлекать сумму/дату из свободного текста, поэтому
        // для такого текста неизбежен вызов Tier 3; Tier 2 всё равно используется ниже —
        // чтобы уточнить категорию результата LLM, если товар/магазин уже был ранее подтверждён.
        if (!networkMonitor.isOnline()) {
            return Resource.Error(
                "Не получилось разобрать фразу локально, а интернет недоступен. " +
                    "Попробуйте сформулировать проще (например: «такси 450») или добавьте операцию вручную."
            )
        }

        val categories = categoryDao.getAllOnce().map { it.toDomain() }
        val defaultAccountName = accountDao.getActiveOnce()
            .firstOrNull { it.name == Constants.DEFAULT_ACCOUNT_NAME }?.name
            ?: accountDao.getActiveOnce().firstOrNull()?.name
            ?: Constants.DEFAULT_ACCOUNT_NAME

        return when (val geminiResult = geminiClient.parseExpenses(sanitizedText, categories, defaultAccountName)) {
            is Resource.Success -> Resource.Success(geminiResult.data.map { resolveCategory(it) })
            is Resource.Error -> geminiResult
            Resource.Loading -> Resource.Loading
        }
    }

    /**
     * Категория результата ЛЮБОГО уровня каскада — CLAUDE.md §3.2. Порядок попыток:
     * 1) персональный кэш `semantic_aliases` (пользователь уже подтверждал это слово);
     * 2) встроенный офлайн-словарь [DefaultKeywordCategories] (типовые "такси", "кофе" и т.п.
     *    — иначе Tier 1, который сам категорию не определяет, до первого запроса к LLM всегда
     *    сваливал бы их в "Другое");
     * 3) если результат УЖЕ содержит категорию (например, её определил Tier 3 при полном
     *    разборе фразы) — ничего не трогаем, повторная классификация не нужна;
     * 4) иначе, для расходов и при наличии сети — точечный вызов Gemini
     *    ([GeminiClient.classifyCategory]) ТОЛЬКО за категорией. Это единственный способ
     *    корректно раскидывать операции по СВОИМ категориям пользователя (например,
     *    "Яндекс Плюс" → созданная пользователем "Подписки"): такие названия в принципе
     *    не могли попасть во встроенный словарь заранее. Результат сразу учится в
     *    personal-кэше — второй раз тот же токен не тратится (тот же принцип, что и для
     *    ответов Tier 3 в §3.2).
     */
    private suspend fun resolveCategory(expense: ParsedExpense): ParsedExpense {
        val keyword = expense.title.normalizeAsAliasKeyword()
        if (keyword.isEmpty()) return expense

        semanticAliasRepository.resolveCategoryId(keyword)?.let { return expense.copy(categoryId = it) }
        DefaultKeywordCategories.keywordToCategoryId[keyword]?.let { return expense.copy(categoryId = it) }

        // Между встроенным словарём и вызовом LLM — офлайн-разбор составных названий по словам:
        // раньше весь title искался как ОДНА строка, поэтому "Подписка Нетфликс" не находила ни
        // "подписка", ни "netflix" по отдельности, и тем более не находила пользовательскую
        // категорию "Подписки" (другое окончание слова). matchByToken проверяет каждое слово
        // фразы отдельно; matchByStem дополнительно сравнивает слова с названиями ПОЛЬЗОВАТЕЛЬСКИХ
        // категорий по совпадению "основы" (первые 5 символов) — закрывает типовые русские
        // словоформы без словаря окончаний и без обращения к сети.
        val tokens = keyword.tokenizeWords()
        matchByToken(tokens)?.let { return expense.copy(categoryId = it) }

        if (expense.categoryId == null) {
            val userCategories = categoryDao.getAllOnce().map { it.toDomain() }
                .filter { it.isIncomeCategory == (expense.transactionType == TransactionType.INCOME) }
            matchByStem(tokens, userCategories)?.let { return expense.copy(categoryId = it) }
        }

        if (expense.categoryId != null) return expense
        if (expense.transactionType != TransactionType.EXPENSE || !networkMonitor.isOnline()) return expense

        val categories = categoryDao.getAllOnce().map { it.toDomain() }
        return when (val result = geminiClient.classifyCategory(expense.title, categories)) {
            is Resource.Success -> {
                semanticAliasRepository.upsert(normalizedKeyword = keyword, categoryId = result.data)
                expense.copy(categoryId = result.data)
            }
            else -> expense
        }
    }

    /** Ищет отдельные слова фразы (а не всю фразу целиком) в персональном кэше и встроенном словаре. */
    private suspend fun matchByToken(tokens: List<String>): Long? {
        for (rawToken in tokens) {
            val token = BRAND_ALIASES[rawToken] ?: rawToken
            semanticAliasRepository.resolveCategoryId(token)?.let { return it }
            DefaultKeywordCategories.keywordToCategoryId[token]?.let { return it }
        }
        return null
    }

    /**
     * Сопоставляет слова фразы с названиями ПОЛЬЗОВАТЕЛЬСКИХ категорий по совпадению основы —
     * "подписка нетфликс" → категория "Подписки" без единого обращения к LLM. См. doc [resolveCategory].
     */
    private fun matchByStem(tokens: List<String>, categories: List<Category>): Long? {
        val categoryStems = categories.map { category ->
            category to category.name.normalizeAsAliasKeyword().tokenizeWords()
                .filter { it.length >= MIN_STEM_WORD_LENGTH }
                .map { it.stem() }
        }
        for (token in tokens) {
            if (token.length < MIN_STEM_WORD_LENGTH) continue
            val tokenStem = token.stem()
            categoryStems.firstOrNull { (_, stems) -> tokenStem in stems }?.let { (category, _) -> return category.id }
        }
        return null
    }
}
