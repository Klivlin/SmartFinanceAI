package ru.vogu.smartfinance.presentation.settings

/**
 * MVI-контракт экрана настроек — CLAUDE.md §5.2/§6: "Управление категориями, экспорт данных,
 * выбор провайдера LLM, ввод собственного API-ключа". Тема и переход к категориям управляются
 * снаружи (см. [ru.vogu.smartfinance.presentation.navigation.SmartFinanceNavGraph]) — тем же
 * способом, что и в ChatScreen, чтобы не дублировать состояние темы в нескольких ViewModel.
 */
sealed interface SettingsIntent {
    data class ApiKeyInputChanged(val value: String) : SettingsIntent
    data object SaveApiKey : SettingsIntent
    data object ResetApiKey : SettingsIntent
    data class ReminderToggled(val enabled: Boolean) : SettingsIntent
    data object ExportRequested : SettingsIntent
    data object MessageShown : SettingsIntent
}

data class SettingsState(
    val apiKeyInput: String = "",
    val hasCustomApiKey: Boolean = false,
    val isReminderEnabled: Boolean = true,
    val isExporting: Boolean = false,
    val message: String? = null
)

sealed interface SettingsEffect {
    /** CSV собран и готов к сохранению/отправке — см. [ru.vogu.smartfinance.domain.usecase.ExportTransactionsUseCase]. */
    data class ExportReady(val csvContent: String, val fileName: String) : SettingsEffect
}
