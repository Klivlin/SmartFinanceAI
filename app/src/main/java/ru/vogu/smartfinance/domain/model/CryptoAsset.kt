package ru.vogu.smartfinance.domain.model

/**
 * Поддерживаемые криптовалюты для счетов типа [ru.vogu.smartfinance.core.database.entity.AccountType.CRYPTO].
 * [code] хранится в поле `currency` счёта (CLAUDE.md §2.1 расширено крипто-счетами — тикер
 * занимает то же место, что и ISO 4217 код у фиатных счетов, поэтому отдельная колонка не нужна).
 * [coingeckoId] — идентификатор монеты в CoinGecko API (см. [ru.vogu.smartfinance.core.network.CryptoExchangeRateApi]).
 */
enum class CryptoAsset(val code: String, val displayName: String, val coingeckoId: String) {
    BTC("BTC", "Bitcoin", "bitcoin"),
    ETH("ETH", "Ethereum", "ethereum"),
    USDT("USDT", "Tether", "tether"),
    USDC("USDC", "USD Coin", "usd-coin"),
    BNB("BNB", "BNB", "binancecoin"),
    SOL("SOL", "Solana", "solana"),
    TON("TON", "Toncoin", "the-open-network"),
    XRP("XRP", "XRP", "ripple"),
    ADA("ADA", "Cardano", "cardano"),
    DOGE("DOGE", "Dogecoin", "dogecoin");

    companion object {
        fun fromCode(code: String): CryptoAsset? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
