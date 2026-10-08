package ru.vogu.smartfinance.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import ru.vogu.smartfinance.core.network.dto.FiatRatesResponseDto

/**
 * Курсы фиатных валют — CLAUDE.md §2.1 (мультивалютность). open.er-api.com — бесплатный,
 * без API-ключа, обновляется раз в сутки (достаточно для персонального финансового учёта).
 */
interface FiatExchangeRateApi {
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String): FiatRatesResponseDto
}
