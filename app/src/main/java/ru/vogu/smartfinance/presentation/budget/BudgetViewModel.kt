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
import ru.vogu.smartfinance.domain.usecase.CreateCategoryUseCase
import ru.vogu.smartfinance.domain.usecase.GetBudgetStatusUseCase
import ru.vogu.smartfinance.domain.usecase.GetIncomeCategoryStatusUseCase
import ru.vogu.smartfinance.domain.usecase.ReorderCategoryUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateCategoryAppearanceUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateCategoryBudgetUseCase
import javax.inject.Inject

/**
 * ViewModel экрана бюджета — CLAUDE.md §2.5/§5.2/§6. Не содержит бизнес-логики: список лимитов —
 * из [GetBudgetStatusUseCase] (реактивно), доходные категории — из [GetIncomeCategoryStatusUseCase]
 * (список доработок, п.5 — расходы и доходы на одной странице), валидация/сохранение суммы — в
 * [UpdateCategoryBudgetUseCase], иконка/цвет категории — в [UpdateCategoryAppearanceUseCase],
 * создание своей категории — в [CreateCategoryUseCase], порядок — в [ReorderCategoryUseCase].
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val getBudgetStatusUseCase: GetBudgetStatusUseCase,
    private val getIncomeCategoryStatusUseCase: GetIncomeCategoryStatusUseCase,
    private val updateCategoryBudgetUseCase: UpdateCategoryBudgetUseCase,
    private val updateCategoryAppearanceUseCase: UpdateCategoryAppearanceUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val reorderCategoryUseCase: ReorderCategoryUseCase
) : ViewModel() {

    private val uiState = MutableStateFlow(BudgetState())

    val state: StateFlow<BudgetState> = combine(
        uiState,
        getBudgetStatusUseCase(),
        getIncomeCategoryStatusUseCase()
    ) { base, expenseItems, incomeItems ->
        base.copy(expenseItems = expenseItems, incomeItems = incomeItems, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetState())

    fun onIntent(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.TabSelected -> uiState.update { it.copy(selectedTab = intent.tab) }
            is BudgetIntent.EditBudgetRequested -> {
                val category = state.value.items.firstOrNull { it.category.id == intent.categoryId }?.category
                uiState.update { it.copy(editingCategory = category) }
            }
            BudgetIntent.DismissEditBudget -> uiState.update { it.copy(editingCategory = null) }
            is BudgetIntent.SaveBudget -> saveBudget(intent.amountText)
            is BudgetIntent.RemoveBudget -> removeBudget(intent.categoryId)
            is BudgetIntent.CustomizeAppearanceRequested -> {
                val category = state.value.items.firstOrNull { it.category.id == intent.categoryId }?.category
                uiState.update { it.copy(editingAppearanceCategory = category) }
            }
            BudgetIntent.DismissCustomizeAppearance -> uiState.update { it.copy(editingAppearanceCategory = null) }
            is BudgetIntent.SaveAppearance -> saveAppearance(intent.iconName, intent.colorHex)
            BudgetIntent.AddCategoryRequested -> uiState.update { it.copy(isAddCategoryVisible = true) }
            BudgetIntent.DismissAddCategory -> uiState.update { it.copy(isAddCategoryVisible = false) }
            is BudgetIntent.SaveNewCategory -> saveNewCategory(intent.name, intent.iconName, intent.colorHex)
            is BudgetIntent.MoveCategory -> moveCategory(intent.categoryId, intent.direction)
            BudgetIntent.ErrorMessageShown -> uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun moveCategory(categoryId: Long, direction: ru.vogu.smartfinance.domain.usecase.ReorderDirection) {
        val orderedCategories = state.value.items.map { it.category }
        viewModelScope.launch {
            when (val result = reorderCategoryUseCase(categoryId, direction, orderedCategories)) {
                is Resource.Error -> uiState.update { it.copy(errorMessage = result.message) }
                is Resource.Success -> Unit
                Resource.Loading -> Unit
            }
        }
    }

    private fun saveNewCategory(name: String, iconName: String, colorHex: String) {
        val isIncomeCategory = uiState.value.selectedTab == CategoryTab.INCOME
        uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = createCategoryUseCase(name, iconName, colorHex, isIncomeCategory = isIncomeCategory)) {
                is Resource.Success -> uiState.update { it.copy(isSaving = false, isAddCategoryVisible = false) }
                is Resource.Error -> uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    private fun saveAppearance(iconName: String, colorHex: String) {
        val categoryId = uiState.value.editingAppearanceCategory?.id ?: return
        uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = updateCategoryAppearanceUseCase(categoryId, iconName, colorHex)) {
                is Resource.Success -> uiState.update { it.copy(isSaving = false, editingAppearanceCategory = null) }
                is Resource.Error -> uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
                Resource.Loading -> Unit
            }
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
