package ru.vogu.smartfinance.domain.repository

import java.math.BigDecimal

/**
 * Курсы валют/криптовалют к рублю — CLAUDE.md §2.1 (мультивалютность, криптосчета). Курс
 * кэшируется локально (см. [ru.vogu.smartfinance.core.database.entity.ExchangeRateEntity]),
 * поэтому смена валюты счёта и переводы между разновалютными счетами продолжают работать
 * (по последнему известному курсу) без интернета — CLAUDE.md §2.7 Offline-First.
 */
interface ExchangeRateRepository {

    /** Сколько рублей стоит одна единица [currencyCode] (ISO 4217 код или тикер криптовалюты). */
    suspend fun getRateToRub(currencyCode: String): BigDecimal

    /** Конвертирует [amount] из [fromCurrency] в [toCurrency] по реальному курсу (через рубль как опорную валюту). */
    suspend fun convert(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal
}
