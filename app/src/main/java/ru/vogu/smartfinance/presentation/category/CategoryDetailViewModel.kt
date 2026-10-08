package ru.vogu.smartfinance.presentation.category

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
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.usecase.GetCategoryTransactionsUseCase
import ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase
import java.math.BigDecimal
import javax.inject.Inject

/**
 * ViewModel экрана "Все траты категории" — CLAUDE.md §5.2/§6. Категория и её операции —
 * реактивно из [GetCategoryTransactionsUseCase]/[CategoryRepository]; удаление — через тот же
 * [UndoTransactionUseCase], что и "Отменить"/"Удалить" в чате (атомарный откат баланса счёта);
 * правка — через тот же [UpdateTransactionUseCase] и тот же BottomSheet-редактор, что и на
 * Action Card в чате (CLAUDE.md — доработка "изменить транзакцию как в чате").
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val getCategoryTransactionsUseCase: GetCategoryTransactionsUseCase,
    private val undoTransactionUseCase: UndoTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase
) : ViewModel() {

    private val categoryId: Long = checkNotNull(savedStateHandle["categoryId"])

    private val uiState = MutableStateFlow(CategoryDetailState())
    val state: StateFlow<CategoryDetailState> = uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val category = categoryRepository.getById(categoryId)
            uiState.update { it.copy(category = category) }
        }
        viewModelScope.launch {
            getCategoryTransactionsUseCase(categoryId).collect { rows ->
                val total = rows
                    .filter { it.transaction.transactionType == TransactionType.EXPENSE }
                    .fold(BigDecimal.ZERO) { acc, row -> acc + row.transaction.amountBase }
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

    fun onIntent(intent: CategoryDetailIntent) {
        when (intent) {
            is CategoryDetailIntent.DeleteRequested ->
                uiState.update { it.copy(pendingDeleteTransactionId = intent.transactionId) }
            CategoryDetailIntent.DeleteDismissed ->
                uiState.update { it.copy(pendingDeleteTransactionId = null) }
            CategoryDetailIntent.DeleteConfirmed -> deletePending()
            is CategoryDetailIntent.EditRequested -> {
                val transaction = uiState.value.rows.firstOrNull { it.transaction.id == intent.transactionId }?.transaction
                uiState.update { it.copy(editingTransaction = transaction) }
            }
            CategoryDetailIntent.DismissEdit -> uiState.update { it.copy(editingTransaction = null) }
            is CategoryDetailIntent.SaveEditedTransaction -> saveEdited(intent.transaction)
            CategoryDetailIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
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
