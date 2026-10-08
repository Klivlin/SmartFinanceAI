package ru.vogu.smartfinance.core.network

import retrofit2.http.GET
import retrofit2.http.Query
import ru.vogu.smartfinance.core.network.dto.CryptoPriceDto

/**
 * Курс криптовалют к рублю — CLAUDE.md §2.1 (криптовалютный счёт). CoinGecko — бесплатный,
 * без API-ключа, публичный `simple/price` эндпоинт.
 */
interface CryptoExchangeRateApi {
    @GET("api/v3/simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "rub"
    ): Map<String, CryptoPriceDto>
}
