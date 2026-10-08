package ru.vogu.smartfinance.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.common.toLocalDate
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.ChatHistoryRepository
import ru.vogu.smartfinance.domain.repository.OnboardingRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import ru.vogu.smartfinance.domain.usecase.ObserveDashboardSummaryUseCase
import ru.vogu.smartfinance.domain.usecase.ProcessNliInputUseCase
import ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

private const val CHAT_HISTORY_LIMIT = 100

/** Опорные справочники (категории/счета) для рендера карточек и BottomSheet-редактора. */
private data class ReferenceData(
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList()
)

/** Чисто UI-состояние, которое не хранится в Room: локальное эхо ввода и статус "подтверждено". */
private data class LocalOverlay(
    val pendingUserMessage: ChatMessage.UserText? = null,
    val confirmedTransactionIds: Set<Long> = emptySet()
)

/**
 * ViewModel главного Chat-экрана — CLAUDE.md §5.1/§6. Единственный источник правды для ленты
 * сообщений — [TransactionRepository.observeRecent]: каждая транзакция с непустым
 * `rawInputText` разворачивается обратно в пару "реплика пользователя + Action Card"
 * (см. [buildMessages]), поэтому история переживает пересоздание экрана/процесса без
 * дублирования локального состояния.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val processNliInputUseCase: ProcessNliInputUseCase,
    private val undoTransactionUseCase: UndoTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    private val uiState = MutableStateFlow(ChatState())
    private val localOverlay = MutableStateFlow(LocalOverlay())

    private val _effect = Channel<ChatEffect>(Channel.BUFFERED)
    val effect: Flow<ChatEffect> = _effect.receiveAsFlow()

    private val referenceData: Flow<ReferenceData> = combine(
        categoryRepository.observeAll(),
        accountRepository.observeAll()
    ) { categories, accounts -> ReferenceData(categories, accounts) }

    val state: StateFlow<ChatState> = combine(
        uiState,
        transactionRepository.observeRecentByEntryOrder(CHAT_HISTORY_LIMIT),
        referenceData,
        localOverlay,
        chatHistoryRepository.clearedAt
    ) { base, transactions, ref, overlay, clearedAt ->
        // "Очистить чат" (CLAUDE.md §5.1) не трогает Room — просто прячет из ленты всё, что
        // было СОЗДАНО до момента очистки; сами операции остаются доступны в "Категориях".
        // Фильтр по createdAt (момент ввода), а не по timestamp (дата самой операции) — иначе
        // операция "вчера такси 500", введённая ПОСЛЕ очистки чата, имела бы timestamp РАНЬШЕ
        // clearedAt и навсегда пропадала бы из чата, хотя создана только что.
        val visibleTransactions = if (clearedAt == null) {
            transactions
        } else {
            transactions.filter { it.createdAt.isAfter(clearedAt) }
        }
        base.copy(
            messages = buildMessages(
                transactions = visibleTransactions,
                categoriesById = ref.categories.associateBy { it.id },
                accountsById = ref.accounts.associateBy { it.id },
                confirmedIds = overlay.confirmedTransactionIds,
                pending = overlay.pendingUserMessage
            ),
            editingCategories = ref.categories,
            editingAccounts = ref.accounts,
            primaryAccount = ref.accounts.firstOrNull { it.isPrimary } ?: ref.accounts.firstOrNull()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatState())

    init {
        viewModelScope.launch {
            observeDashboardSummaryUseCase().collect { summary ->
                uiState.update { it.copy(dashboard = summary) }
            }
        }
        viewModelScope.launch {
            val alreadyPrompted = onboardingRepository.isInitialBalancePrompted.first()
            if (!alreadyPrompted) {
                uiState.update { it.copy(showInitialBalanceDialog = true) }
            }
        }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputTextChanged -> uiState.update { it.copy(inputText = intent.text) }
            ChatIntent.SendMessage -> sendMessage()
            is ChatIntent.ConfirmTransaction -> confirmTransaction(intent.transactionId)
            is ChatIntent.UndoTransaction -> undoTransaction(intent.transactionId)
            is ChatIntent.RestoreTransaction -> restoreTransaction(intent.transaction)
            is ChatIntent.EditTransactionRequested -> requestEdit(intent.transactionId)
            ChatIntent.DismissEdit -> uiState.update { it.copy(editingTransaction = null) }
            is ChatIntent.SaveEditedTransaction -> saveEdited(intent.transaction)
            ChatIntent.MicClicked -> viewModelScope.launch { _effect.send(ChatEffect.RequestVoiceInput) }
            is ChatIntent.VoiceResult -> onVoiceResult(intent.recognizedText)
            ChatIntent.VoiceRecognitionFailed -> uiState.update {
                it.copy(errorMessage = "Не удалось распознать голос. Попробуйте ещё раз или введите текст.")
            }
            ChatIntent.CameraClicked -> viewModelScope.launch { _effect.send(ChatEffect.NavigateToScanner) }
            ChatIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
            ChatIntent.HeaderExpandToggled -> uiState.update { it.copy(isHeaderExpanded = !it.isHeaderExpanded) }
            ChatIntent.ClearChatRequested -> uiState.update { it.copy(isClearChatDialogVisible = true) }
            ChatIntent.ClearChatDismissed -> uiState.update { it.copy(isClearChatDialogVisible = false) }
            ChatIntent.ClearChatConfirmed -> clearChat()
            is ChatIntent.InitialBalanceConfirmed -> confirmInitialBalance(intent.amountText)
            ChatIntent.InitialBalanceSkipped -> dismissInitialBalanceDialog()
        }
    }

    private fun confirmInitialBalance(amountText: String) {
        val amount = amountText.trim().replace(',', '.').toBigDecimalOrNull()
        if (amount == null) {
            uiState.update { it.copy(errorMessage = "Введите корректную сумму баланса.") }
            return
        }
        viewModelScope.launch {
            val account = accountRepository.findBestMatch(null)
            accountRepository.adjustBalance(account.id, amount - account.balance)
            onboardingRepository.markInitialBalancePrompted()
            uiState.update { it.copy(showInitialBalanceDialog = false) }
        }
    }

    private fun dismissInitialBalanceDialog() {
        viewModelScope.launch {
            onboardingRepository.markInitialBalancePrompted()
            uiState.update { it.copy(showInitialBalanceDialog = false) }
        }
    }

    private fun clearChat() {
        uiState.update { it.copy(isClearChatDialogVisible = false) }
        viewModelScope.launch { chatHistoryRepository.clearChat() }
    }

    private fun onVoiceResult(recognizedText: String) {
        if (recognizedText.isBlank()) return
        uiState.update { it.copy(inputText = recognizedText) }
        sendMessage()
    }

    private fun sendMessage() {
        val text = uiState.value.inputText.trim()
        if (text.isEmpty() || uiState.value.isSending) return

        localOverlay.update {
            it.copy(
                pendingUserMessage = ChatMessage.UserText(
                    stableId = "pending-${Instant.now().toEpochMilli()}",
                    timestamp = Instant.now(),
                    text = text,
                    isPending = true
                )
            )
        }
        uiState.update { it.copy(inputText = "", isSending = true) }

        viewModelScope.launch {
            when (val result = processNliInputUseCase(text)) {
                is Resource.Success -> {
                    // Настоящие Action Card-ы придут из observeRecent() — Room остаётся
                    // единственным источником правды, локальное эхо больше не нужно.
                    localOverlay.update { it.copy(pendingUserMessage = null) }
                    uiState.update { it.copy(isSending = false) }
                    // Операция "задним числом" ("вчера такси 500") сортируется по РЕАЛЬНОЙ
                    // дате среди всех сообщений ленты — если в чате уже есть более свежие
                    // записи за сегодня, новая карточка попадёт выше них, а не в самый низ, и
                    // ScrollToBottom её не покажет. Без явного подтверждения это выглядит так,
                    // будто фраза "не распозналась", хотя операция уже создана — см. историю
                    // чата или экран "Категории".
                    val backdated = result.data.filter { it.timestamp.toLocalDate() != LocalDate.now() }
                    if (backdated.isNotEmpty()) {
                        val dateLabel = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                            .withLocale(Locale("ru", "RU"))
                            .format(backdated.first().timestamp.toLocalDate())
                        _effect.send(ChatEffect.ShowToast("Добавлено датой: $dateLabel — смотрите выше в ленте"))
                    }
                    _effect.send(ChatEffect.ScrollToBottom)
                }
                is Resource.Error -> {
                    localOverlay.update { it.copy(pendingUserMessage = null) }
                    uiState.update { it.copy(isSending = false, errorMessage = result.message) }
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun confirmTransaction(transactionId: Long) {
        localOverlay.update { it.copy(confirmedTransactionIds = it.confirmedTransactionIds + transactionId) }
    }

    private fun undoTransaction(transactionId: Long) {
        viewModelScope.launch {
            // Undo стирает запись целиком (см. doc-комментарий UndoTransactionUseCase) — читаем
            // её ДО удаления, иначе кнопке "Отменить" на уведомлении будет нечего восстанавливать.
            val transactionToDelete = transactionRepository.getById(transactionId)
            when (val result = undoTransactionUseCase(transactionId)) {
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Success -> {
                    localOverlay.update { it.copy(confirmedTransactionIds = it.confirmedTransactionIds - transactionId) }
                    if (transactionToDelete != null) {
                        _effect.send(ChatEffect.TransactionDeleted(transactionToDelete))
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.createTransaction(
                accountId = transaction.accountId,
                toAccountId = transaction.toAccountId,
                categoryId = transaction.categoryId,
                amount = transaction.amount,
                transactionType = transaction.transactionType,
                title = transaction.title,
                notes = transaction.notes,
                timestamp = transaction.timestamp,
                source = transaction.source,
                rawInputText = transaction.rawInputText,
                originalCurrency = transaction.originalCurrency,
                transferFeeAmount = transaction.transferFeeAmount,
                toAmount = transaction.toAmount,
                recurringRuleId = transaction.recurringRuleId,
                splitItems = transaction.splitItems
            )
            _effect.send(ChatEffect.ScrollToBottom)
        }
    }

    private fun requestEdit(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getById(transactionId)
            if (transaction == null) {
                uiState.update { it.copy(errorMessage = "Операция не найдена — возможно, уже удалена.") }
            } else {
                uiState.update { it.copy(editingTransaction = transaction) }
            }
        }
    }

    private fun saveEdited(transaction: Transaction) {
        viewModelScope.launch {
            when (val result = updateTransactionUseCase(transaction)) {
                is Resource.Success -> uiState.update { it.copy(editingTransaction = null) }
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun buildMessages(
        transactions: List<Transaction>,
        categoriesById: Map<Long, Category>,
        accountsById: Map<Long, Account>,
        confirmedIds: Set<Long>,
        pending: ChatMessage.UserText?
    ): List<ChatMessage> {
        val ascending = transactions.sortedBy { it.createdAt }
        val result = mutableListOf<ChatMessage>()
        var lastRawInput: String? = null

        for (tx in ascending) {
            val rawInput = tx.rawInputText
            if (rawInput != null && rawInput != lastRawInput) {
                result += ChatMessage.UserText(
                    stableId = "user-${tx.id}",
                    timestamp = tx.createdAt,
                    text = rawInput
                )
            }
            lastRawInput = rawInput

            result += ChatMessage.TransactionCard(
                stableId = "tx-${tx.id}",
                timestamp = tx.createdAt,
                transaction = tx,
                category = tx.categoryId?.let { categoriesById[it] },
                account = accountsById[tx.accountId],
                toAccount = tx.toAccountId?.let { accountsById[it] },
                isConfirmed = tx.id in confirmedIds,
                splitItemCategories = tx.splitItems.map { it.categoryId?.let { id -> categoriesById[id] } }
            )
        }

        pending?.let { result += it }
        return result
    }
}
