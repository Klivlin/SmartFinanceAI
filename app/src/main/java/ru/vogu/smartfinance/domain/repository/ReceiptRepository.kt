package ru.vogu.smartfinance.domain.repository

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.FiscalReceiptData
import ru.vogu.smartfinance.domain.model.ParsedReceiptItem

/** Оркестрация двух путей сканера чеков — CLAUDE.md §4. */
interface ReceiptRepository {

    /** Локальный разбор кассового QR (стандарт ФНС) — не требует сети. Null — QR не распознан. */
    fun parseFiscalQr(rawQrValue: String): FiscalReceiptData?

    /**
     * OCR-путь: локальная предфильтрация строк + Tier 3 (Gemini) для сплит-категоризации
     * позиций чека — CLAUDE.md §4.2.
     */
    suspend fun parseOcrLines(rawLines: List<String>): Resource<List<ParsedReceiptItem>>
}
