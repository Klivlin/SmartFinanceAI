package ru.vogu.smartfinance.presentation.chat

import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.DashboardSummary
import ru.vogu.smartfinance.domain.model.Transaction
import java.time.Instant

/**
 * MVI-контракт главного Chat-экрана (Conversational Home Screen) — CLAUDE.md §5.1, §6.
 * ChatViewModel не содержит бизнес-логики — только маппинг [ChatIntent] в вызовы UseCase-ов
 * и сборку [ChatState] из реактивных потоков (Room — единственный источник правды).
 */

sealed interface ChatIntent {
    data class InputTextChanged(val text: String) : ChatIntent
    data object SendMessage : ChatIntent
    data class ConfirmTransaction(val transactionId: Long) : ChatIntent
    data class UndoTransaction(val transactionId: Long) : ChatIntent
    data class EditTransactionRequested(val transactionId: Long) : ChatIntent
    data object DismissEdit : ChatIntent
    data class SaveEditedTransaction(val transaction: Transaction) : ChatIntent
    data object MicClicked : ChatIntent
    data class VoiceResult(val recognizedText: String) : ChatIntent
    data object VoiceRecognitionFailed : ChatIntent
    data object CameraClicked : ChatIntent
    data object ErrorMessageShown : ChatIntent
    data object HeaderExpandToggled : ChatIntent
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isHeaderExpanded: Boolean = true,
    val editingTransaction: Transaction? = null,
    val editingCategories: List<Category> = emptyList(),
    val editingAccounts: List<Account> = emptyList(),
    val dashboard: DashboardSummary? = null,
    val errorMessage: String? = null
) {
    val canSend: Boolean get() = inputText.isNotBlank() && !isSending
}

sealed interface ChatMessage {
    val stableId: String
    val timestamp: Instant

    /** Реплика пользователя. [isPending] — локальное оптимистичное эхо до подтверждения из Room. */
    data class UserText(
        override val stableId: String,
        override val timestamp: Instant,
        val text: String,
        val isPending: Boolean = false
    ) : ChatMessage

    /** Action Card — CLAUDE.md §5.1: интерактивная карточка вместо текстового ответа системы. */
    data class TransactionCard(
        override val stableId: String,
        override val timestamp: Instant,
        val transaction: Transaction,
        val category: Category?,
        val account: Account?,
        val toAccount: Account?,
        val isConfirmed: Boolean
    ) : ChatMessage
}

sealed interface ChatEffect {
    data object ScrollToBottom : ChatEffect
    data object NavigateToScanner : ChatEffect
    data object RequestVoiceInput : ChatEffect
    data class ShowToast(val message: String) : ChatEffect
}
