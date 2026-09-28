package ru.vogu.smartfinance.core.security

import javax.inject.Inject

/** Категория обнаруженных персональных данных — для UI-индикатора "часть текста скрыта перед отправкой в ИИ". */
enum class PiiCategory {
    CARD_NUMBER,
    CVV,
    PHONE,
    FULL_NAME,
    BALANCE
}

data class PiiFilterResult(
    val sanitizedText: String,
    val redactedCategories: Set<PiiCategory>
) {
    val hasPii: Boolean get() = redactedCategories.isNotEmpty()
}

/**
 * Локальный фильтр PII — CLAUDE.md §2.6. Выполняется ПЕРВЫМ шагом каскада (до Tier 1/2/3,
 * см. диаграмму §3), т.к. даже Tier 1 (regex) и Tier 2 (semantic cache) не должны сохранять
 * чувствительные данные в rawInputText/семантическом кэше на диске, не говоря уже об
 * отправке в Gemini на Tier 3.
 *
 * Это эвристический фильтр (регулярные выражения), а не NER-модель: он ловит типовые
 * форматы карт/телефонов/CVV и подписанные ключевыми словами балансы, но не гарантирует
 * 100% полноты для ФИО (см. комментарий у [FULL_NAME_REGEX]). Это осознанный компромисс —
 * тяжёлая NER-модель на устройстве противоречила бы требованию "0 токенов / минимум ресурсов"
 * для локальных тиров каскада.
 */
class PiiFilter @Inject constructor() {

    fun sanitize(rawText: String): PiiFilterResult {
        var text = rawText
        val redacted = mutableSetOf<PiiCategory>()

        // Порядок важен: CVV/телефон/баланс — до карты, т.к. номер карты (13-19 цифр)
        // не должен "съедать" более специфичные короткие паттерны рядом с ключевыми словами.
        text = replaceIfMatched(text, CVV_REGEX, "[CVV]", PiiCategory.CVV, redacted)
        text = replaceIfMatched(text, PHONE_REGEX, "[ТЕЛЕФОН]", PiiCategory.PHONE, redacted)
        text = replaceIfMatched(text, BALANCE_REGEX, "[БАЛАНС]", PiiCategory.BALANCE, redacted)
        text = replaceIfMatched(text, CARD_NUMBER_REGEX, "[КАРТА]", PiiCategory.CARD_NUMBER, redacted)
        text = replaceIfMatched(text, FULL_NAME_REGEX, "[ИМЯ]", PiiCategory.FULL_NAME, redacted)

        return PiiFilterResult(sanitizedText = text, redactedCategories = redacted)
    }

    private fun replaceIfMatched(
        text: String,
        regex: Regex,
        replacement: String,
        category: PiiCategory,
        redacted: MutableSet<PiiCategory>
    ): String {
        if (!regex.containsMatchIn(text)) return text
        redacted += category
        return regex.replace(text, replacement)
    }

    private companion object {
        /** Номер карты: 4 блока по 4 цифры (с пробелом/дефисом/слитно), последний блок 1-4 цифры. */
        val CARD_NUMBER_REGEX = Regex("""\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{1,4}\b""")

        /** CVV/CVC рядом с ключевым словом — "cvv 123", "cvc: 456", "код безопасности 789". */
        val CVV_REGEX = Regex(
            """(?i)\b(cvv2?|cvc2?|код\s*безопасности|код\s*карты)\s*[:\-]?\s*\d{3,4}\b"""
        )

        /** Российский номер телефона: +7/8, опциональные скобки/пробелы/дефисы. */
        val PHONE_REGEX = Regex(
            """(?:\+7|7|8)[\s\-]?\(?\d{3}\)?[\s\-]?\d{3}[\s\-]?\d{2}[\s\-]?\d{2}\b"""
        )

        /**
         * Баланс/остаток, явно помеченный ключевым словом — НЕ трогает обычные суммы транзакций
         * ("кофе 250"), т.к. без ключевого слова regex не сработает.
         */
        val BALANCE_REGEX = Regex(
            """(?i)\b(баланс|остаток(?:\s+средств)?)\s*[:\-]?\s*\d+(?:[.,]\d{1,2})?\s*(?:₽|руб|rub)?\b"""
        )

        /**
         * Эвристика ФИО: 2-3 идущих подряд слова с заглавной буквы (Фамилия Имя Отчество).
         * Ложные срабатывания возможны на названиях мест с несколькими заглавными словами —
         * это приемлемо для локального фильтра, единственная задача которого — не дать
         * персональным данным уйти во внешний API (при сомнении лучше вырезать лишнее слово).
         */
        val FULL_NAME_REGEX = Regex(
            """\b[А-ЯЁ][а-яё]+\s+[А-ЯЁ][а-яё]+(?:\s+[А-ЯЁ][а-яё]+)?\b"""
        )
    }
}
