package ru.vogu.smartfinance.data.parser

import ru.vogu.smartfinance.domain.model.FiscalOperationType
import ru.vogu.smartfinance.domain.model.FiscalReceiptData
import java.math.BigDecimal
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Парсер кассового QR-кода (стандарт ФНС РФ) — CLAUDE.md §4.1.
 *
 * Формат: `t=20260928T1430&s=1540.50&fn=99990789&i=12345&fp=987654321&n=1`.
 * Ключи: t — дата/время, s — сумма, fn — номер фискального накопителя,
 * i — номер фискального документа, fp — фискальный признак, n — тип операции.
 *
 * Не требует сети — вся транзакция формируется локально из данных QR, без
 * обращения к платным API проверки чеков (ФНС API / ОФД).
 */
class FiscalQrParser @Inject constructor() {

    /** Возвращает null при некорректном/неполном QR — обрабатывается как "чек не распознан" (CLAUDE.md §9.4). */
    fun parse(rawQrValue: String): FiscalReceiptData? {
        val params = parseParams(rawQrValue)

        val t = params["t"] ?: return null
        val s = params["s"] ?: return null
        val fn = params["fn"] ?: return null
        val i = params["i"] ?: return null
        val fp = params["fp"] ?: return null
        val n = params["n"]

        val timestamp = parseTimestamp(t) ?: return null
        val amount = parseAmount(s) ?: return null
        if (amount.signum() <= 0) return null

        val operationType = n?.toIntOrNull()?.let { FiscalOperationType.fromCode(it) }
            ?: FiscalOperationType.SALE

        return FiscalReceiptData(
            timestamp = timestamp,
            amount = amount,
            fiscalDriveNumber = fn,
            fiscalDocumentNumber = i,
            fiscalSign = fp,
            operationType = operationType,
            rawQrValue = rawQrValue
        )
    }

    private fun parseParams(raw: String): Map<String, String> =
        raw
            .trim()
            .split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = pair.substring(0, idx).trim().lowercase()
                val rawValue = pair.substring(idx + 1).trim()
                val value = try {
                    URLDecoder.decode(rawValue, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    rawValue
                }
                key to value
            }
            .toMap()

    private fun parseTimestamp(raw: String): Instant? {
        for (formatter in TIMESTAMP_FORMATTERS) {
            try {
                val localDateTime = LocalDateTime.parse(raw, formatter)
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant()
            } catch (e: DateTimeParseException) {
                // пробуем следующий формат
            }
        }
        return null
    }

    private fun parseAmount(raw: String): BigDecimal? = try {
        BigDecimal(raw)
    } catch (e: NumberFormatException) {
        null
    }

    private companion object {
        // ФНС допускает оба варианта: с секундами и без.
        val TIMESTAMP_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
        )
    }
}
