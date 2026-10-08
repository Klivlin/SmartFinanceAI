package ru.vogu.smartfinance.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.domain.repository.ReminderSettingsRepository
import javax.inject.Inject

class ReminderSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ReminderSettingsRepository {

    override val isReminderEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[REMINDER_ENABLED_KEY] ?: true }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REMINDER_ENABLED_KEY] = enabled }
    }

    private companion object {
        val REMINDER_ENABLED_KEY = booleanPreferencesKey("expense_reminder_enabled")
    }
}
