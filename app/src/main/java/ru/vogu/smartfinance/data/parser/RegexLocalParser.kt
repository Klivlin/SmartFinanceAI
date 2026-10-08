package ru.vogu.smartfinance.data.parser

import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.AccountHint
import ru.vogu.smartfinance.domain.model.ParsedExpense
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * Tier 1 каскада — CLAUDE.md §3.1. Локальный regex-парсер, 0 токенов, 1-3мс.
 *
 * Намеренно ограничен простыми конструкциями из спеки: `"[Сумма] [Товар]"`,
 * `"[Товар] [Сумма]"`, опционально с ключевым словом счёта и/или относительной датой
 * ("вчера", "сегодня", "позавчера" — см. [extractDateHint]). Всё, что не укладывается
 * в эти паттерны (несколько операций в одном сообщении, переводы со сложной
 * формулировкой, произвольные даты вида "3 марта") — возвращает null, и обработку
 * продолжает Tier 2 (semantic cache) / Tier 3 (LLM). Это осознанное сужение охвата ради
 * 100% точности на "лёгких" 80% случаев — компромисс, а не недоработка.
 *
 * Доход ("зарплата 50000", "кэшбэк 250 с карты") определяется по [INCOME_KEYWORDS] —
 * только однозначные слова без риска перепутать направление денег. Неоднозначные формулировки
 * ("вернул долг" — не ясно, отдал я или мне вернули) намеренно НЕ входят в список и эскалируются
 * к Tier 3 (LLM), которая разбирает их по контексту всей фразы.
 */
class RegexLocalParser @Inject constructor() {

    fun tryParse(rawInputText: String): ParsedExpense? {
        val trimmed = rawInputText.trim()
        if (trimmed.isEmpty()) return null

        val (accountHint, textWithoutAccount) = extractAccountHint(trimmed)
        val (dateHint, textWithoutDate) = extractDateHint(textWithoutAccount)
        val text = textWithoutDate.trim()
        if (text.isEmpty()) return null

        val leadingMatch = LEADING_AMOUNT_REGEX.matchEntire(text)
        val trailingMatch = TRAILING_AMOUNT_REGEX.matchEntire(text)
        val bareAmountMatch = BARE_AMOUNT_REGEX.matchEntire(text)

        // Если под оба паттерна одновременно подходит фраза с числом и в начале, и в конце
        // ("500 такси 500") — это неоднозначность, безопаснее эскалировать выше по каскаду.
        if (leadingMatch != null && trailingMatch != null) return null

        val (amountRaw, title) = when {
            leadingMatch != null -> leadingMatch.groupValues[1] to leadingMatch.groupValues[2]
            trailingMatch != null -> trailingMatch.groupValues[2] to trailingMatch.groupValues[1]
            // "крипта 400", "наличкой 500" — после вырезания ключевого слова счёта из текста
            // не осталось отдельного названия операции (само слово счёта и БЫЛО единственным
            // содержательным словом фразы). Без этой ветки такие фразы проваливались бы в Tier 3
            // (LLM), которая ничего не знает про счета пользователя и списывала бы с дефолтного
            // счёта вместо угаданного по ключевому слову — именно так дал сбой крипто-счёт.
            bareAmountMatch != null && accountHint != null -> bareAmountMatch.groupValues[1] to accountHint.rawKeyword
            else -> return null
        }

        val amount = parseAmount(amountRaw) ?: return null
        if (amount.signum() <= 0) return null // CLAUDE.md §9.4: отрицательные/нулевые суммы — невалидны

        val cleanTitle = title.trim().replaceFirstChar { it.uppercaseChar() }
        if (cleanTitle.isEmpty()) return null

        return ParsedExpense(
            amount = amount,
            title = cleanTitle,
            transactionType = detectTransactionType(text),
            source = TransactionSource.NLI,
            accountHint = accountHint,
            categoryId = null, // категория резолвится отдельно через Tier2 semantic cache / Tier3 LLM
            date = dateHint ?: LocalDate.now(),
            rawInputText = rawInputText
        )
    }

    /** Доход — если во фразе встретилось хотя бы одно однозначное слово из [INCOME_KEYWORDS]. */
    private fun detectTransactionType(text: String): TransactionType {
        val words = text.lowercase(Locale("ru", "RU")).split(Regex("[^а-яё]+")).filter { it.isNotBlank() }
        return if (words.any { it in INCOME_KEYWORDS }) TransactionType.INCOME else TransactionType.EXPENSE
    }

    /**
     * Ищет относительную дату ("вчера", "сегодня", "позавчера") и вырезает соответствующее
     * слово из текста, чтобы оно не попало в [title]. Абсолютные даты ("3 марта", "01.02")
     * намеренно не поддерживаются — редкий случай в живом вводе, эскалируется к Tier 3.
     */
    private fun extractDateHint(text: String): Pair<LocalDate?, String> {
        for ((phraseRegex, offsetDays) in DATE_KEYWORD_PATTERNS) {
            val match = phraseRegex.find(text) ?: continue
            val cleaned = phraseRegex.replace(text, "").trim()
            return LocalDate.now().minusDays(offsetDays) to cleaned
        }
        return null to text
    }

    private fun parseAmount(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(',', '.'))
    } catch (e: NumberFormatException) {
        null
    }

    /**
     * Ищет ключевое слово счёта ("наличкой", "с тинька", "сбера"...) и вырезает
     * соответствующий фрагмент из текста, чтобы он не попал в [title].
     */
    private fun extractAccountHint(text: String): Pair<AccountHint?, String> {
        for ((phraseRegex, canonicalKeyword) in ACCOUNT_KEYWORD_PATTERNS) {
            val match = phraseRegex.find(text) ?: continue
            val rawKeyword = match.groupValues[1]
            // normalizedKeyword — канонический ярлык (не сырой текст): "нал"/"наличкой"/"наличными"
            // должны давать один и тот же ключ, чтобы UseCase мог сопоставлять его с типом/именем
            // реального счёта без разбора всех словоформ.
            val hint = AccountHint(
                rawKeyword = rawKeyword,
                normalizedKeyword = canonicalKeyword
            )
            val cleaned = phraseRegex.replace(text, "").trim()
            return hint to cleaned
        }
        return null to text
    }

    private companion object {
        /** "1200 пятерочка", "250 кофе" — число в начале, остальное — название. */
        val LEADING_AMOUNT_REGEX = Regex("""^(\d+(?:[.,]\d{1,2})?)\s+(.+)$""")

        /** "такси 450", "кофе 250" — число в конце. */
        val TRAILING_AMOUNT_REGEX = Regex("""^(.+?)\s+(\d+(?:[.,]\d{1,2})?)$""")

        /** Голая сумма без названия — используется только вместе с [AccountHint] (см. [tryParse]). */
        val BARE_AMOUNT_REGEX = Regex("""^(\d+(?:[.,]\d{1,2})?)$""")

        /**
         * Фразы-указатели счёта. Группа 1 — само ключевое слово (используется как
         * [AccountHint.rawKeyword] для сопоставления с реальными счетами в UseCase/репозитории).
         * Предлог перед словом ("с", "со", "на") включён в паттерн, чтобы вырезаться целиком.
         */
        // Правая граница слова через lookahead, а не `\b`: стандартный Java/ICU `\b` завязан
        // на ASCII-класс \w и не считает кириллицу "буквой" — на реальном Android-устройстве
        // (ICU-движок regex) это даже приводило к PatternSyntaxException на флаге `(?U)`,
        // а не только к молчаливому непопаданию границы. Портируемая и предсказуемая замена
        // на обоих движках (host JVM в юнит-тестах и Android ICU на устройстве).
        const val WORD_END = """(?=[^a-zа-яё]|$)"""

        /**
         * Относительные даты — CLAUDE.md §5.1 распознавание дат в чате. Смещение в днях от
         * сегодняшней даты; "позавчера" проверяется раньше "вчера" на случай пересечения,
         * хотя границы слова ([WORD_END] + обязательный пробел/начало строки перед словом)
         * и так исключают ложное совпадение "вчера" внутри "позавчера".
         */
        val DATE_KEYWORD_PATTERNS: List<Pair<Regex, Long>> = listOf(
            Regex("""(?i)(?:^|\s)(позавчера)$WORD_END""") to 2L,
            Regex("""(?i)(?:^|\s)(вчера)$WORD_END""") to 1L,
            Regex("""(?i)(?:^|\s)(сегодня)$WORD_END""") to 0L
        )

        val ACCOUNT_KEYWORD_PATTERNS: List<Pair<Regex, String>> = listOf(
            // Тикеры криптовалют — CLAUDE.md §2.1: проверяются РАНЬШЕ generic-слова "крипта",
            // чтобы "крипта 400 юсдт" резолвился в конкретный счёт с валютой USDT, а не в первый
            // попавшийся крипто-счёт (иначе 400 могло бы случайно списаться как 400 BTC).
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(юсдт|usdt|тезер|тезера|тетер)$WORD_END""") to "usdt",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(юсдц|usdc)$WORD_END""") to "usdc",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(биткоин[а-яё]*|биток[а-яё]*|битки|btc)$WORD_END""") to "btc",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(эфириум[а-яё]*|эфир[а-яё]*|eth)$WORD_END""") to "eth",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(тонкоин[а-яё]*|ton)$WORD_END""") to "ton",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(бинанс\s*ко[ий]н[а-яё]*|bnb)$WORD_END""") to "bnb",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(солан[а-яё]*|sol)$WORD_END""") to "sol",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(рипл[а-яё]*|xrp)$WORD_END""") to "xrp",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(кардано|ada)$WORD_END""") to "ada",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(додж[а-яё]*|doge)$WORD_END""") to "doge",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(наличкой|наличными|нал(?:ом)?)$WORD_END""") to "cash",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(тинько?ф+|т-банка?|тбанка?)$WORD_END""") to "tinkoff",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(сбер(?:а|банка)?)$WORD_END""") to "sber",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(альфа(?:-?банка)?)$WORD_END""") to "alfa",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(озон\s*банка?|ozon\s*bank)$WORD_END""") to "ozon",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(втб)$WORD_END""") to "vtb",
            Regex("""(?i)(?:^|\s)(?:с|со)?\s*(карты|картой)$WORD_END""") to "card",
            // Generic-слово "крипта" — самое последнее: любой конкретный тикер выше по списку
            // должен победить, если во фразе упомянут ("крипта 400 юсдт" -> счёт по валюте USDT).
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(крипт[а-яё]*)$WORD_END""") to "crypto"
        )

        /**
         * Однозначные маркеры дохода (нормализованные слова, без словоформ с двойным толкованием).
         * Включает распространённые аббревиатуры (CLAUDE.md §5.1: "ЗП" → зарплата) — сравнение
         * идёт по уже lowercase-нормализованным словам, поэтому "ЗП"/"зп" сюда попадают одинаково.
         */
        val INCOME_KEYWORDS: Set<String> = setOf(
            "зарплата", "зарплату", "зп", "аванс", "доход", "кэшбэк", "кешбэк", "премия", "премию",
            "стипендия", "стипендию", "пенсия", "пенсию", "гонорар", "бонус", "выигрыш",
            "подарили", "перевели", "заработал", "заработала", "продал", "продала"
        )
    }
}
