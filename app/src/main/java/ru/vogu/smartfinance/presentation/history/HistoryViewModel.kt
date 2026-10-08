package ru.vogu.smartfinance.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.usecase.GetTransactionHistoryUseCase
import ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase
import javax.inject.Inject

/**
 * ViewModel экрана "История операций" — CLAUDE.md список доработок, п.2/§6. Список операций —
 * реактивно из [GetTransactionHistoryUseCase]; правка/удаление — те же UseCase, что и в чате
 * и на экране категории (единая точка бизнес-логики, ViewModel только диспетчерит интенты).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val undoTransactionUseCase: UndoTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase
) : ViewModel() {

    private val uiState = MutableStateFlow(HistoryState())

    val state: StateFlow<HistoryState> = combine(
        uiState,
        getTransactionHistoryUseCase(),
        categoryRepository.observeAll(),
        accountRepository.observeAll()
    ) { base, rows, categories, accounts ->
        base.copy(
            rows = rows,
            editingCategories = categories,
            editingAccounts = accounts,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.FilterChanged -> uiState.update { it.copy(filter = intent.filter) }
            is HistoryIntent.EditRequested -> {
                val transaction = state.value.rows.firstOrNull { it.transaction.id == intent.transactionId }?.transaction
                uiState.update { it.copy(editingTransaction = transaction) }
            }
            HistoryIntent.DismissEdit -> uiState.update { it.copy(editingTransaction = null) }
            is HistoryIntent.SaveEditedTransaction -> saveEdited(intent.transaction)
            is HistoryIntent.DeleteRequested -> uiState.update { it.copy(pendingDeleteTransactionId = intent.transactionId) }
            HistoryIntent.DeleteDismissed -> uiState.update { it.copy(pendingDeleteTransactionId = null) }
            HistoryIntent.DeleteConfirmed -> deletePending()
            HistoryIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
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

    private fun deletePending() {
        val transactionId = uiState.value.pendingDeleteTransactionId ?: return
        uiState.update { it.copy(pendingDeleteTransactionId = null) }
        viewModelScope.launch {
            when (val result = undoTransactionUseCase(transactionId)) {
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Success -> Unit
                Resource.Loading -> Unit
            }
        }
    }
}
