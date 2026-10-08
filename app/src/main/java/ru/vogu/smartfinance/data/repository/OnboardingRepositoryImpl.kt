package ru.vogu.smartfinance.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.domain.repository.OnboardingRepository
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : OnboardingRepository {

    override val isInitialBalancePrompted: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[INITIAL_BALANCE_PROMPTED_KEY] ?: false }

    override suspend fun markInitialBalancePrompted() {
        dataStore.edit { prefs -> prefs[INITIAL_BALANCE_PROMPTED_KEY] = true }
    }

    private companion object {
        val INITIAL_BALANCE_PROMPTED_KEY = booleanPreferencesKey("initial_balance_prompted")
    }
}
