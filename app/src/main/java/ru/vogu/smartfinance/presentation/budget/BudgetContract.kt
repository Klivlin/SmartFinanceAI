package ru.vogu.smartfinance.presentation.budget

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus

/**
 * MVI-контракт экрана бюджета — CLAUDE.md §2.5/§5.2/§6. Позволяет установить/изменить/снять
 * месячный лимит по каждой категории расходов; сам расчёт "сколько уже потрачено" —
 * [ru.vogu.smartfinance.domain.usecase.GetBudgetStatusUseCase] (реактивно, из Room).
 */
sealed interface BudgetIntent {
    data class EditBudgetRequested(val categoryId: Long) : BudgetIntent
    data object DismissEditBudget : BudgetIntent
    data class SaveBudget(val amountText: String) : BudgetIntent
    data class RemoveBudget(val categoryId: Long) : BudgetIntent
    data object ErrorMessageShown : BudgetIntent
}

data class BudgetState(
    val items: List<CategoryBudgetStatus> = emptyList(),
    val editingCategory: Category? = null,
    val baseCurrency: String = Constants.DEFAULT_BASE_CURRENCY,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true
)
