package ru.vogu.smartfinance.data.parser

import javax.inject.Inject

/**
 * Локальная предфильтрация строк OCR кассового чека — CLAUDE.md §4.2. Выполняется ДО
 * отправки в Gemini (Tier 3): отбрасывает служебные строки чека (ИНН, реквизиты ККТ, адрес,
 * способ оплаты, итоговые суммы) и оставляет только строки, похожие на позицию товара с ценой.
 * Как и для текстового ввода (§3), цель — минимизировать объём текста, уходящего во внешний
 * API: из полного OCR-дампа чека (30-60 строк) обычно остаётся только 5-15 строк позиций.
 *
 * Это эвристический фильтр по regex, а не ML-классификатор строк — сознательный компромисс
 * ради простоты и 0 дополнительных токенов на этом шаге (см. аналогичное решение для
 * [ru.vogu.smartfinance.core.security.PiiFilter]).
 */
class ReceiptOcrParser @Inject constructor() {

    fun extractCandidateLines(rawLines: List<String>): List<String> =
        rawLines
            .map { it.trim() }
            .filter { it.length >= MIN_ITEM_LINE_LENGTH }
            .filterNot { line -> NOISE_PATTERNS.any { pattern -> pattern.containsMatchIn(line) } }
            .filter { PRICE_REGEX.containsMatchIn(it) }

    private companion object {
        const val MIN_ITEM_LINE_LENGTH = 4

        /** Любое число вида 123.45 / 123,45 — минимальный сигнал "в строке есть цена". */
        val PRICE_REGEX = Regex("""\d+[.,]\d{2}""")

        /** Служебные строки чека, которые не являются позициями товара/услуги — CLAUDE.md §4.2. */
        val NOISE_PATTERNS = listOf(
            Regex("""(?i)\bинн\b"""),
            Regex("""(?i)\bкасси?р"""),
            Regex("""(?i)\bсно\b"""),
            Regex("""(?i)\bфн\s*№?\s*\d"""),
            Regex("""(?i)\bфд\s*№?\s*\d"""),
            Regex("""(?i)\bфп\s*№?\s*\d"""),
            Regex("""(?i)рн\s*ккт"""),
            Regex("""(?i)зн\s*ккт"""),
            Regex("""(?i)\bофд\b"""),
            Regex("""(?i)смен[аы]\s*№?\s*\d"""),
            Regex("""(?i)чек\s*№"""),
            Regex("""(?i)итог"""),
            Regex("""(?i)сумма\s*(к\s*оплате|расч[её]та|наличн|безналичн)"""),
            Regex("""(?i)наличн(ыми|ая)"""),
            Regex("""(?i)безналичн"""),
            Regex("""(?i)сдач[аи]"""),
            Regex("""(?i)получено"""),
            Regex("""(?i)\bндс\b"""),
            Regex("""(?i)www\."""),
            Regex("""[\w.+-]+@[\w-]+\.[\w.]+"""), // email
            Regex("""(?:\+7|8)[\s\-]?\(?\d{3}\)?[\s\-]?\d{3}[\s\-]?\d{2}[\s\-]?\d{2}"""), // телефон
            Regex("""(?i)\bг\.\s?[А-ЯЁ]"""), // "г. Москва" — начало адреса
            Regex("""(?i)\bул\.\s?[А-ЯЁ]""") // "ул. Ленина" — адрес
        )
    }
}
