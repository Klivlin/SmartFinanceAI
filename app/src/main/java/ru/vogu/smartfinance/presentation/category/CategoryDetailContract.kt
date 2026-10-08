package ru.vogu.smartfinance.presentation.category

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.usecase.CategoryTransactionRow
import java.math.BigDecimal

/**
 * Экран "Все траты категории" — CLAUDE.md §5.2. Открывается тапом по строке категории на
 * [ru.vogu.smartfinance.presentation.budget.BudgetScreen]. Действия — удаление операции
 * (см. [ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase]) и её правка тем же
 * BottomSheet-редактором, что и на карточках чата (CLAUDE.md — п.8 списка доработок:
 * "изменить транзакцию как в чате"); навигация назад — обычный колбэк Compose, а не MVI-эффект.
 */
sealed interface CategoryDetailIntent {
    data class DeleteRequested(val transactionId: Long) : CategoryDetailIntent
    data object DeleteDismissed : CategoryDetailIntent
    data object DeleteConfirmed : CategoryDetailIntent
    data class EditRequested(val transactionId: Long) : CategoryDetailIntent
    data object DismissEdit : CategoryDetailIntent
    data class SaveEditedTransaction(val transaction: Transaction) : CategoryDetailIntent
    data object ErrorMessageShown : CategoryDetailIntent
}

data class CategoryDetailState(
    val category: Category? = null,
    val rows: List<CategoryTransactionRow> = emptyList(),
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val baseCurrency: String = Constants.DEFAULT_BASE_CURRENCY,
    val isLoading: Boolean = true,
    val pendingDeleteTransactionId: Long? = null,
    val editingTransaction: Transaction? = null,
    val editingCategories: List<Category> = emptyList(),
    val editingAccounts: List<Account> = emptyList(),
    val errorMessage: String? = null
)
