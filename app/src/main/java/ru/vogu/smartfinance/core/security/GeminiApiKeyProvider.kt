package ru.vogu.smartfinance.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.vogu.smartfinance.BuildConfig
import ru.vogu.smartfinance.core.common.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ключ Gemini API: пользователь может ввести свой собственный в настройках
 * (CLAUDE.md §5.2 "ввод собственного API-ключа"), тогда используется он, иначе — ключ
 * приложения по умолчанию из `BuildConfig.GEMINI_API_KEY` (CLAUDE.md §9.2).
 * Пользовательский ключ хранится в EncryptedSharedPreferences, а не в открытом виде.
 */
@Singleton
class GeminiApiKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            Constants.KEYSTORE_DB_PASSPHRASE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiKey(): String {
        val userKey = securePrefs.getString(Constants.KEYSTORE_GEMINI_USER_API_KEY, null)
        return if (!userKey.isNullOrBlank()) userKey else BuildConfig.GEMINI_API_KEY
    }

    fun setUserApiKey(apiKey: String?) {
        securePrefs.edit().apply {
            if (apiKey.isNullOrBlank()) remove(Constants.KEYSTORE_GEMINI_USER_API_KEY)
            else putString(Constants.KEYSTORE_GEMINI_USER_API_KEY, apiKey.trim())
        }.apply()
    }

    fun hasAnyApiKey(): Boolean = getApiKey().isNotBlank()
}
