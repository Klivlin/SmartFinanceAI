package ru.vogu.smartfinance.presentation.budget

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus
import ru.vogu.smartfinance.domain.usecase.ReorderDirection

/**
 * MVI-контракт экрана бюджета — CLAUDE.md §2.5/§5.2/§6. Позволяет установить/изменить/снять
 * месячный лимит по каждой категории расходов; сам расчёт "сколько уже потрачено" —
 * [ru.vogu.smartfinance.domain.usecase.GetBudgetStatusUseCase] (реактивно, из Room).
 */
/** Вкладка экрана "Категории" — CLAUDE.md список доработок, п.5 (расходы и доходы на одной странице). */
enum class CategoryTab { EXPENSE, INCOME }

sealed interface BudgetIntent {
    data class TabSelected(val tab: CategoryTab) : BudgetIntent
    data class EditBudgetRequested(val categoryId: Long) : BudgetIntent
    data object DismissEditBudget : BudgetIntent
    data class SaveBudget(val amountText: String) : BudgetIntent
    data class RemoveBudget(val categoryId: Long) : BudgetIntent

    /** Тап по иконке категории — открывает выбор иконки/цвета (CLAUDE.md §5.2). */
    data class CustomizeAppearanceRequested(val categoryId: Long) : BudgetIntent
    data object DismissCustomizeAppearance : BudgetIntent
    data class SaveAppearance(val iconName: String, val colorHex: String) : BudgetIntent

    /** "+" на экране "Категории" — своя категория расходов, CLAUDE.md §5.2. */
    data object AddCategoryRequested : BudgetIntent
    data object DismissAddCategory : BudgetIntent
    data class SaveNewCategory(val name: String, val iconName: String, val colorHex: String) : BudgetIntent

    /** "↑"/"↓" на строке категории — CLAUDE.md §5.2. */
    data class MoveCategory(val categoryId: Long, val direction: ReorderDirection) : BudgetIntent

    data object ErrorMessageShown : BudgetIntent
}

data class BudgetState(
    val selectedTab: CategoryTab = CategoryTab.EXPENSE,
    val expenseItems: List<CategoryBudgetStatus> = emptyList(),
    val incomeItems: List<CategoryBudgetStatus> = emptyList(),
    val editingCategory: Category? = null,
    val editingAppearanceCategory: Category? = null,
    val isAddCategoryVisible: Boolean = false,
    val baseCurrency: String = Constants.DEFAULT_BASE_CURRENCY,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true
) {
    /** Список активной вкладки — UI всегда рендерит именно его, не заботясь о том, какая вкладка выбрана. */
    val items: List<CategoryBudgetStatus>
        get() = if (selectedTab == CategoryTab.EXPENSE) expenseItems else incomeItems
}
