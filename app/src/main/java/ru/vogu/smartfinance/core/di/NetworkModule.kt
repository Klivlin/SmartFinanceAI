package ru.vogu.smartfinance.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.vogu.smartfinance.BuildConfig
import ru.vogu.smartfinance.core.network.CryptoExchangeRateApi
import ru.vogu.smartfinance.core.network.FiatExchangeRateApi
import ru.vogu.smartfinance.core.network.GeminiApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // В release НИКОГДА не логируем тела запросов/ответов — там проходит (уже
            // PII-отфильтрованный) текст пользователя и суммы; в debug — для отладки промпта.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.GEMINI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideGeminiApi(retrofit: Retrofit): GeminiApi = retrofit.create(GeminiApi::class.java)

    /** Отдельный Retrofit с другим base URL — курсы фиатных валют (CLAUDE.md §2.1), см. [FiatRatesRetrofit]. */
    @Provides
    @Singleton
    @FiatRatesRetrofit
    fun provideFiatRatesRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /** Отдельный Retrofit с другим base URL — курсы криптовалют (CLAUDE.md §2.1), см. [CryptoRatesRetrofit]. */
    @Provides
    @Singleton
    @CryptoRatesRetrofit
    fun provideCryptoRatesRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideFiatExchangeRateApi(@FiatRatesRetrofit retrofit: Retrofit): FiatExchangeRateApi =
        retrofit.create(FiatExchangeRateApi::class.java)

    @Provides
    @Singleton
    fun provideCryptoExchangeRateApi(@CryptoRatesRetrofit retrofit: Retrofit): CryptoExchangeRateApi =
        retrofit.create(CryptoExchangeRateApi::class.java)
}
