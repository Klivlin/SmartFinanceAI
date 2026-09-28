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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import ru.vogu.smartfinance.domain.usecase.ObserveDashboardSummaryUseCase
import ru.vogu.smartfinance.domain.usecase.ProcessNliInputUseCase
import ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase
import java.time.Instant
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
    private val accountRepository: AccountRepository
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
        transactionRepository.observeRecent(CHAT_HISTORY_LIMIT),
        referenceData,
        localOverlay
    ) { base, transactions, ref, overlay ->
        base.copy(
            messages = buildMessages(
                transactions = transactions,
                categoriesById = ref.categories.associateBy { it.id },
                accountsById = ref.accounts.associateBy { it.id },
                confirmedIds = overlay.confirmedTransactionIds,
                pending = overlay.pendingUserMessage
            ),
            editingCategories = ref.categories,
            editingAccounts = ref.accounts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatState())

    init {
        viewModelScope.launch {
            observeDashboardSummaryUseCase().collect { summary ->
                uiState.update { it.copy(dashboard = summary) }
            }
        }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputTextChanged -> uiState.update { it.copy(inputText = intent.text) }
            ChatIntent.SendMessage -> sendMessage()
            is ChatIntent.ConfirmTransaction -> confirmTransaction(intent.transactionId)
            is ChatIntent.UndoTransaction -> undoTransaction(intent.transactionId)
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
        }
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
            when (val result = undoTransactionUseCase(transactionId)) {
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Success -> {
                    localOverlay.update { it.copy(confirmedTransactionIds = it.confirmedTransactionIds - transactionId) }
                    _effect.send(ChatEffect.ShowToast("Операция отменена"))
                }
                Resource.Loading -> Unit
            }
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
                isConfirmed = tx.id in confirmedIds
            )
        }

        pending?.let { result += it }
        return result
    }
}
