package ru.vogu.smartfinance.presentation.budget

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
import ru.vogu.smartfinance.domain.usecase.GetBudgetStatusUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateCategoryBudgetUseCase
import javax.inject.Inject

/**
 * ViewModel экрана бюджета — CLAUDE.md §2.5/§6. Не содержит бизнес-логики: список лимитов —
 * из [GetBudgetStatusUseCase] (реактивно), валидация/сохранение суммы — в
 * [UpdateCategoryBudgetUseCase].
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val getBudgetStatusUseCase: GetBudgetStatusUseCase,
    private val updateCategoryBudgetUseCase: UpdateCategoryBudgetUseCase
) : ViewModel() {

    private val uiState = MutableStateFlow(BudgetState())

    val state: StateFlow<BudgetState> = combine(uiState, getBudgetStatusUseCase()) { base, items ->
        base.copy(items = items, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetState())

    fun onIntent(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.EditBudgetRequested -> {
                val category = state.value.items.firstOrNull { it.category.id == intent.categoryId }?.category
                uiState.update { it.copy(editingCategory = category) }
            }
            BudgetIntent.DismissEditBudget -> uiState.update { it.copy(editingCategory = null) }
            is BudgetIntent.SaveBudget -> saveBudget(intent.amountText)
            is BudgetIntent.RemoveBudget -> removeBudget(intent.categoryId)
            BudgetIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun saveBudget(amountText: String) {
        val categoryId = uiState.value.editingCategory?.id ?: return
        val amount = amountText.trim().replace(',', '.').toBigDecimalOrNull()
        if (amount == null) {
            uiState.update { it.copy(errorMessage = "Введите корректную сумму лимита.") }
            return
        }

        uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = updateCategoryBudgetUseCase(categoryId, amount)) {
                is Resource.Success -> uiState.update { it.copy(isSaving = false, editingCategory = null) }
                is Resource.Error -> uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun removeBudget(categoryId: Long) {
        viewModelScope.launch {
            when (val result = updateCategoryBudgetUseCase(categoryId, null)) {
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Success -> Unit
                Resource.Loading -> Unit
            }
        }
    }
}
