package ru.vogu.smartfinance.data.repository

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.NetworkMonitor
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.common.normalizeAsAliasKeyword
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.network.GeminiClient
import ru.vogu.smartfinance.core.security.PiiFilter
import ru.vogu.smartfinance.data.mapper.toDomain
import ru.vogu.smartfinance.data.parser.RegexLocalParser
import ru.vogu.smartfinance.domain.model.ParsedExpense
import ru.vogu.smartfinance.domain.repository.NliExpenseRepository
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import javax.inject.Inject

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

        // Tier 1: локальный regex, 0 токенов.
        val tier1Result = regexLocalParser.tryParse(sanitizedText)
        if (tier1Result != null) {
            return Resource.Success(listOf(resolveCategoryViaCache(tier1Result)))
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
            is Resource.Success -> Resource.Success(geminiResult.data.map { resolveCategoryViaCache(it) })
            is Resource.Error -> geminiResult
            Resource.Loading -> Resource.Loading
        }
    }

    /**
     * Tier 2 — CLAUDE.md §3.2. Если нормализованное название операции уже встречалось
     * (пользователь ранее подтвердил категорию), подменяем categoryId результата Tier 1/Tier 3
     * на закешированный — это и есть "в следующий раз система не дёргает LLM, а берёт категорию
     * из локальной базы" в применении к части задачи "категория" (амаунт/дата уже разобраны
     * выше без обращения к сети).
     */
    private suspend fun resolveCategoryViaCache(expense: ParsedExpense): ParsedExpense {
        val keyword = expense.title.normalizeAsAliasKeyword()
        if (keyword.isEmpty()) return expense
        val cachedCategoryId = semanticAliasRepository.resolveCategoryId(keyword) ?: return expense
        return expense.copy(categoryId = cachedCategoryId)
    }
}
