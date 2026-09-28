package ru.vogu.smartfinance.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.vogu.smartfinance.core.common.Constants
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выдаёт passphrase для шифрования Room-базы через SQLCipher (CLAUDE.md §2.6).
 *
 * Passphrase — 256-битное случайное значение, генерируется один раз при первом запуске
 * и хранится в [EncryptedSharedPreferences], ключ шифрования которых, в свою очередь,
 * защищён Android Keystore (аппаратный модуль, если доступен на устройстве). Сам passphrase
 * никогда не покидает устройство и не передаётся ни в один внешний сервис.
 */
@Singleton
class DatabasePassphraseProvider @Inject constructor(
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

    /** Возвращает существующий passphrase или генерирует и сохраняет новый (только при первом запуске). */
    @Synchronized
    fun getOrCreatePassphrase(): CharArray {
        val existing = securePrefs.getString(Constants.KEYSTORE_DB_PASSPHRASE_KEY, null)
        if (existing != null) {
            return existing.toCharArray()
        }
        val generated = generateRandomPassphrase()
        securePrefs.edit().putString(Constants.KEYSTORE_DB_PASSPHRASE_KEY, generated).apply()
        return generated.toCharArray()
    }

    private fun generateRandomPassphrase(): String {
        val randomBytes = ByteArray(32) // 256 бит
        SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
