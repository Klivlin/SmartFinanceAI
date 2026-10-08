package ru.vogu.smartfinance.data.repository

import ru.vogu.smartfinance.core.database.dao.ExchangeRateDao
import ru.vogu.smartfinance.core.database.entity.ExchangeRateEntity
import ru.vogu.smartfinance.core.network.CryptoExchangeRateApi
import ru.vogu.smartfinance.core.network.FiatExchangeRateApi
import ru.vogu.smartfinance.domain.model.CryptoAsset
import ru.vogu.smartfinance.domain.repository.ExchangeRateRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class ExchangeRateRepositoryImpl @Inject constructor(
    private val fiatApi: FiatExchangeRateApi,
    private val cryptoApi: CryptoExchangeRateApi,
    private val exchangeRateDao: ExchangeRateDao
) : ExchangeRateRepository {

    override suspend fun getRateToRub(currencyCode: String): BigDecimal {
        val code = currencyCode.trim().uppercase()
        if (code == "RUB") return BigDecimal.ONE

        val cached = exchangeRateDao.getByCode(code)
        val isFresh = cached != null && Duration.between(cached.updatedAt, Instant.now()) < CACHE_TTL
        if (isFresh) return cached!!.rateToRub

        val fetched = runCatching {
            if (CryptoAsset.fromCode(code) != null) fetchCryptoRate(code) else fetchFiatRate(code)
        }.getOrNull()

        if (fetched != null) {
            exchangeRateDao.upsert(ExchangeRateEntity(currencyCode = code, rateToRub = fetched, updatedAt = Instant.now()))
            return fetched
        }

        return cached?.rateToRub
            ?: throw IllegalStateException(
                "Не удалось получить курс $code → RUB. Проверьте интернет-соединение и попробуйте снова."
            )
    }

    override suspend fun convert(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal {
        val from = fromCurrency.trim().uppercase()
        val to = toCurrency.trim().uppercase()
        if (from == to) return amount

        val amountInRub = amount.multiply(getRateToRub(from))
        val scale = if (CryptoAsset.fromCode(to) != null) CRYPTO_SCALE else FIAT_SCALE
        return amountInRub.divide(getRateToRub(to), scale, RoundingMode.HALF_EVEN)
    }

    private suspend fun fetchFiatRate(code: String): BigDecimal? {
        val response = fiatApi.getLatestRates(base = code)
        val rubPerUnit = response.rates["RUB"] ?: return null
        return BigDecimal(rubPerUnit.toString())
    }

    private suspend fun fetchCryptoRate(code: String): BigDecimal? {
        val asset = CryptoAsset.fromCode(code) ?: return null
        val response = cryptoApi.getPrices(ids = asset.coingeckoId)
        val price = response[asset.coingeckoId]?.rub ?: return null
        return BigDecimal(price.toString())
    }

    private companion object {
        val CACHE_TTL: Duration = Duration.ofHours(1)
        const val FIAT_SCALE = 2
        const val CRYPTO_SCALE = 8
    }
}
