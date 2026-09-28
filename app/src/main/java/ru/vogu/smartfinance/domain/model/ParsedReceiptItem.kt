package ru.vogu.smartfinance.domain.model

import java.math.BigDecimal

/**
 * Одна позиция товарного чека, разобранная из OCR-строк через Gemini (Tier 3) —
 * CLAUDE.md §4.2. В отличие от [SplitItem] ещё не привязана к сохранённой транзакции
 * (нет `id`/`transactionId`) — это промежуточный результат парсинга, из которого
 * [ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase] собирает и сохраняет транзакцию.
 */
data class ParsedReceiptItem(
    val name: String,
    val amount: BigDecimal,
    val categoryId: Long?
)
