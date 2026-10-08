package ru.vogu.smartfinance.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.usecase.GetAccountTransactionsUseCase
import ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase
import java.math.BigDecimal
import javax.inject.Inject

/**
 * ViewModel экрана "Все операции счёта" — по аналогии с [ru.vogu.smartfinance.presentation.category.CategoryDetailViewModel],
 * но группировка по счёту, а не по категории. Итог ("Всего потрачено") считается в СОБСТВЕННОЙ
 * валюте счёта (а не базовой), т.к. счёт может быть в другой валюте или крипте.
 */
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val getAccountTransactionsUseCase: GetAccountTransactionsUseCase,
    private val undoTransactionUseCase: UndoTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle["accountId"])

    private val uiState = MutableStateFlow(AccountDetailState())
    val state: StateFlow<AccountDetailState> = uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val account = accountRepository.getById(accountId)
            uiState.update { it.copy(account = account) }
        }
        viewModelScope.launch {
            getAccountTransactionsUseCase(accountId).collect { rows ->
                val total = rows
                    .filterNot { it.isIncoming }
                    .fold(BigDecimal.ZERO) { acc, row -> acc + row.signedAmount.negate() }
                uiState.update { it.copy(rows = rows, totalSpent = total, isLoading = false) }
            }
        }
        viewModelScope.launch {
            combine(categoryRepository.observeAll(), accountRepository.observeAll()) { categories, accounts ->
                categories to accounts
            }.collect { (categories, accounts) ->
                uiState.update { it.copy(editingCategories = categories, editingAccounts = accounts) }
            }
        }
    }

    fun onIntent(intent: AccountDetailIntent) {
        when (intent) {
            is AccountDetailIntent.DeleteRequested ->
                uiState.update { it.copy(pendingDeleteTransactionId = intent.transactionId) }
            AccountDetailIntent.DeleteDismissed ->
                uiState.update { it.copy(pendingDeleteTransactionId = null) }
            AccountDetailIntent.DeleteConfirmed -> deletePending()
            is AccountDetailIntent.EditRequested -> {
                val transaction = uiState.value.rows.firstOrNull { it.transaction.id == intent.transactionId }?.transaction
                uiState.update { it.copy(editingTransaction = transaction) }
            }
            AccountDetailIntent.DismissEdit -> uiState.update { it.copy(editingTransaction = null) }
            is AccountDetailIntent.SaveEditedTransaction -> saveEdited(intent.transaction)
            AccountDetailIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun saveEdited(transaction: ru.vogu.smartfinance.domain.model.Transaction) {
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
