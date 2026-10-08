package ru.vogu.smartfinance.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.domain.repository.ChatHistoryRepository
import java.time.Instant
import javax.inject.Inject

class ChatHistoryRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ChatHistoryRepository {

    override val clearedAt: Flow<Instant?> =
        dataStore.data.map { prefs -> prefs[CHAT_CLEARED_AT_KEY]?.let { Instant.ofEpochMilli(it) } }

    override suspend fun clearChat() {
        dataStore.edit { prefs -> prefs[CHAT_CLEARED_AT_KEY] = Instant.now().toEpochMilli() }
    }

    private companion object {
        val CHAT_CLEARED_AT_KEY = longPreferencesKey("chat_cleared_at")
    }
}
