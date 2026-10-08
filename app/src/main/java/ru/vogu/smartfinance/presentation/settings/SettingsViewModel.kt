package ru.vogu.smartfinance.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.security.GeminiApiKeyProvider
import ru.vogu.smartfinance.domain.repository.ReminderSettingsRepository
import ru.vogu.smartfinance.domain.usecase.ExportTransactionsUseCase
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** ViewModel экрана настроек — CLAUDE.md §6. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyProvider: GeminiApiKeyProvider,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val exportTransactionsUseCase: ExportTransactionsUseCase
) : ViewModel() {

    private data class LocalState(
        val apiKeyInput: String = "",
        val hasCustomApiKey: Boolean = false,
        val isExporting: Boolean = false,
        val message: String? = null
    )

    private val localState = MutableStateFlow(LocalState(hasCustomApiKey = apiKeyProvider.hasUserApiKey()))

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect: Flow<SettingsEffect> = _effect.receiveAsFlow()

    val state: StateFlow<SettingsState> = combine(
        localState,
        reminderSettingsRepository.isReminderEnabled
    ) { local, reminderEnabled ->
        SettingsState(
            apiKeyInput = local.apiKeyInput,
            hasCustomApiKey = local.hasCustomApiKey,
            isReminderEnabled = reminderEnabled,
            isExporting = local.isExporting,
            message = local.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ApiKeyInputChanged -> localState.update { it.copy(apiKeyInput = intent.value) }
            SettingsIntent.SaveApiKey -> saveApiKey()
            SettingsIntent.ResetApiKey -> resetApiKey()
            is SettingsIntent.ReminderToggled -> viewModelScope.launch {
                reminderSettingsRepository.setReminderEnabled(intent.enabled)
            }
            SettingsIntent.ExportRequested -> exportCsv()
            SettingsIntent.MessageShown -> localState.update { it.copy(message = null) }
        }
    }

    private fun saveApiKey() {
        val trimmed = localState.value.apiKeyInput.trim()
        if (trimmed.isEmpty()) return
        apiKeyProvider.setUserApiKey(trimmed)
        localState.update { it.copy(apiKeyInput = "", hasCustomApiKey = true, message = "Ключ сохранён") }
    }

    private fun resetApiKey() {
        apiKeyProvider.setUserApiKey(null)
        localState.update { it.copy(hasCustomApiKey = false, message = "Возвращён ключ приложения по умолчанию") }
    }

    private fun exportCsv() {
        if (localState.value.isExporting) return
        localState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val csv = exportTransactionsUseCase()
            val fileName = "smartfinance_${FILE_DATE_FORMATTER.format(Instant.now())}.csv"
            localState.update { it.copy(isExporting = false) }
            _effect.send(SettingsEffect.ExportReady(csv, fileName))
        }
    }

    private companion object {
        val FILE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(java.time.ZoneId.systemDefault())
    }
}
