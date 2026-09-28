package ru.vogu.smartfinance.domain.model

import java.math.BigDecimal
import java.time.Instant

/** Тип операции по фискальному QR — параметр `n` в стандарте ФНС РФ (CLAUDE.md §4.1). */
enum class FiscalOperationType(val code: Int) {
    SALE(1),
    SALE_RETURN(2),
    PURCHASE(3),
    PURCHASE_RETURN(4);

    companion object {
        fun fromCode(code: Int): FiscalOperationType =
            entries.firstOrNull { it.code == code } ?: SALE
    }
}

/** Данные, извлечённые из кассового QR-кода (стандарт ФНС РФ) — CLAUDE.md §4.1. */
data class FiscalReceiptData(
    val timestamp: Instant,
    val amount: BigDecimal,
    val fiscalDriveNumber: String,   // fn
    val fiscalDocumentNumber: String, // i
    val fiscalSign: String,          // fp
    val operationType: FiscalOperationType, // n
    val rawQrValue: String
)
