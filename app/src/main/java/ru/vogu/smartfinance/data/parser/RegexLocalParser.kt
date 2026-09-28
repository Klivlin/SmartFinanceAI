package ru.vogu.smartfinance.data.parser

import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.AccountHint
import ru.vogu.smartfinance.domain.model.ParsedExpense
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Tier 1 каскада — CLAUDE.md §3.1. Локальный regex-парсер, 0 токенов, 1-3мс.
 *
 * Намеренно ограничен простыми конструкциями из спеки: `"[Сумма] [Товар]"`,
 * `"[Товар] [Сумма]"`, опционально с ключевым словом счёта. Всё, что не укладывается
 * в эти паттерны (относительные даты "вчера", несколько операций в одном сообщении,
 * доходы/переводы со сложной формулировкой) — возвращает null, и обработку продолжает
 * Tier 2 (semantic cache) / Tier 3 (LLM). Это осознанное сужение охвата ради 100%
 * точности на "лёгких" 80% случаев — компромисс, а не недоработка.
 */
class RegexLocalParser @Inject constructor() {

    fun tryParse(rawInputText: String): ParsedExpense? {
        val trimmed = rawInputText.trim()
        if (trimmed.isEmpty()) return null

        val (accountHint, textWithoutAccount) = extractAccountHint(trimmed)
        val text = textWithoutAccount.trim()
        if (text.isEmpty()) return null

        val leadingMatch = LEADING_AMOUNT_REGEX.matchEntire(text)
        val trailingMatch = TRAILING_AMOUNT_REGEX.matchEntire(text)

        // Если под оба паттерна одновременно подходит фраза с числом и в начале, и в конце
        // ("500 такси 500") — это неоднозначность, безопаснее эскалировать выше по каскаду.
        if (leadingMatch != null && trailingMatch != null) return null

        val (amountRaw, title) = when {
            leadingMatch != null -> leadingMatch.groupValues[1] to leadingMatch.groupValues[2]
            trailingMatch != null -> trailingMatch.groupValues[2] to trailingMatch.groupValues[1]
            else -> return null
        }

        val amount = parseAmount(amountRaw) ?: return null
        if (amount.signum() <= 0) return null // CLAUDE.md §9.4: отрицательные/нулевые суммы — невалидны

        val cleanTitle = title.trim().replaceFirstChar { it.uppercaseChar() }
        if (cleanTitle.isEmpty()) return null

        return ParsedExpense(
            amount = amount,
            title = cleanTitle,
            transactionType = TransactionType.EXPENSE,
            source = TransactionSource.NLI,
            accountHint = accountHint,
            categoryId = null, // категория резолвится отдельно через Tier2 semantic cache / Tier3 LLM
            rawInputText = rawInputText
        )
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

        /**
         * Фразы-указатели счёта. Группа 1 — само ключевое слово (используется как
         * [AccountHint.rawKeyword] для сопоставления с реальными счетами в UseCase/репозитории).
         * Предлог перед словом ("с", "со", "на") включён в паттерн, чтобы вырезаться целиком.
         */
        val ACCOUNT_KEYWORD_PATTERNS: List<Pair<Regex, String>> = listOf(
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(наличкой|наличными|нал(?:ом)?)\b""") to "cash",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(тинько?ф+|т-банка?|тбанка?)\b""") to "tinkoff",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(сбер(?:а|банка)?)\b""") to "sber",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(альфа(?:-?банка)?)\b""") to "alfa",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(озон\s*банка?|ozon\s*bank)\b""") to "ozon",
            Regex("""(?i)(?:^|\s)(?:с|со|на)?\s*(втб)\b""") to "vtb",
            Regex("""(?i)(?:^|\s)(?:с|со)?\s*(карты|картой)\b""") to "card"
        )
    }
}
