package ru.vogu.smartfinance.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.domain.repository.ThemeRepository
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ThemeRepository {

    override val isDarkTheme: Flow<Boolean?> =
        dataStore.data.map { prefs -> prefs[DARK_THEME_KEY] }

    override suspend fun setDarkTheme(isDark: Boolean?) {
        dataStore.edit { prefs ->
            if (isDark == null) {
                prefs.remove(DARK_THEME_KEY)
            } else {
                prefs[DARK_THEME_KEY] = isDark
            }
        }
    }

    private companion object {
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_override")
    }
}
