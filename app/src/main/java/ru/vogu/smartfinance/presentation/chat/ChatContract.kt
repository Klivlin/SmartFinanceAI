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

    /** Возврат удалённой операции по нажатию "Отменить" на уведомлении (см. [ChatEffect.TransactionDeleted]). */
    data class RestoreTransaction(val transaction: Transaction) : ChatIntent
    data class EditTransactionRequested(val transactionId: Long) : ChatIntent
    data object DismissEdit : ChatIntent
    data class SaveEditedTransaction(val transaction: Transaction) : ChatIntent
    data object MicClicked : ChatIntent
    data class VoiceResult(val recognizedText: String) : ChatIntent
    data object VoiceRecognitionFailed : ChatIntent
    data object CameraClicked : ChatIntent
    data object ErrorMessageShown : ChatIntent
    data object HeaderExpandToggled : ChatIntent

    /** "Очистить чат" — CLAUDE.md §5.1: скрывает ленту, транзакции не удаляются (см. [ru.vogu.smartfinance.domain.repository.ChatHistoryRepository]). */
    data object ClearChatRequested : ChatIntent
    data object ClearChatConfirmed : ChatIntent
    data object ClearChatDismissed : ChatIntent

    /** Диалог стартового баланса при первом запуске — CLAUDE.md список доработок, п.3. */
    data class InitialBalanceConfirmed(val amountText: String) : ChatIntent
    data object InitialBalanceSkipped : ChatIntent
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    // Свёрнута по умолчанию — на главном экране должно быть минимум "шума", сначала лента
    // операций, детали баланса (сегодня/месяц/бюджет) — по явному тапу.
    val isHeaderExpanded: Boolean = false,
    val editingTransaction: Transaction? = null,
    val editingCategories: List<Category> = emptyList(),
    val editingAccounts: List<Account> = emptyList(),
    /** Счёт, свёрнуто отображаемый в Dashboard Header — CLAUDE.md §5.1 (выбирается на экране "Счета"). */
    val primaryAccount: Account? = null,
    val dashboard: DashboardSummary? = null,
    val errorMessage: String? = null,
    val isClearChatDialogVisible: Boolean = false,
    val showInitialBalanceDialog: Boolean = false
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

    /**
     * Action Card — CLAUDE.md §5.1: интерактивная карточка вместо текстового ответа системы.
     *
     * [splitItemCategories] — категории позиций сплит-чека (CLAUDE.md §2.3/§4.2), по индексу
     * параллельно [Transaction.splitItems]. Без этого поля карточка сканера показывала бы
     * только обезличенное "Чек (N позиций)" на общую сумму — хотя каждая позиция уже
     * категоризирована через Gemini ([ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase]),
     * это было бы не видно пользователю (CLAUDE.md §5.1 требует видеть состав операции, а не
     * только итог).
     */
    data class TransactionCard(
        override val stableId: String,
        override val timestamp: Instant,
        val transaction: Transaction,
        val category: Category?,
        val account: Account?,
        val toAccount: Account?,
        val isConfirmed: Boolean,
        val splitItemCategories: List<Category?> = emptyList()
    ) : ChatMessage
}

sealed interface ChatEffect {
    data object ScrollToBottom : ChatEffect
    data object NavigateToScanner : ChatEffect
    data object RequestVoiceInput : ChatEffect
    data class ShowToast(val message: String) : ChatEffect

    /**
     * Операция удалена — CLAUDE.md §5.1. Несёт саму удалённую [transaction] (а не только id):
     * Undo — это полное удаление записи ([ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase]),
     * поэтому чтобы кнопка "Отменить" на уведомлении могла восстановить операцию, у экрана
     * должны быть под рукой все её поля, а не только идентификатор уже стёртой записи.
     */
    data class TransactionDeleted(val transaction: Transaction) : ChatEffect
}
