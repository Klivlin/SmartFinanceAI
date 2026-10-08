package ru.vogu.smartfinance.core.network.dto

import kotlinx.serialization.Serializable

/** Ответ open.er-api.com/v6/latest/{base} — курсы фиатных валют CLAUDE.md §2.1. */
@Serializable
data class FiatRatesResponseDto(
    val result: String? = null,
    val rates: Map<String, Double> = emptyMap()
)

/** Один элемент ответа CoinGecko `simple/price` (`{"bitcoin": {"rub": 1234.5}}`). */
@Serializable
data class CryptoPriceDto(
    val rub: Double? = null
)
