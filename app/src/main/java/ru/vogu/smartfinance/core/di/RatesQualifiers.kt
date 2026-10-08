package ru.vogu.smartfinance.core.di

import javax.inject.Qualifier

/** Отдельный [retrofit2.Retrofit] с base URL open.er-api.com — курсы фиатных валют (CLAUDE.md §2.1). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FiatRatesRetrofit

/** Отдельный [retrofit2.Retrofit] с base URL api.coingecko.com — курсы криптовалют (CLAUDE.md §2.1). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CryptoRatesRetrofit
