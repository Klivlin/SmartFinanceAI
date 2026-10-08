package ru.vogu.smartfinance.presentation.accounts

import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.usecase.AccountTransactionRow
import java.math.BigDecimal

/**
 * Экран "Все операции счёта" — открывается тапом по строке счёта на [AccountsScreen]. Правка/
 * удаление переиспользуют тот же BottomSheet-редактор и [ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase],
 * что и карточки чата/экран "Категория" — по аналогии с [ru.vogu.smartfinance.presentation.category.CategoryDetailContract].
 */
sealed interface AccountDetailIntent {
    data class DeleteRequested(val transactionId: Long) : AccountDetailIntent
    data object DeleteDismissed : AccountDetailIntent
    data object DeleteConfirmed : AccountDetailIntent
    data class EditRequested(val transactionId: Long) : AccountDetailIntent
    data object DismissEdit : AccountDetailIntent
    data class SaveEditedTransaction(val transaction: Transaction) : AccountDetailIntent
    data object ErrorMessageShown : AccountDetailIntent
}

data class AccountDetailState(
    val account: Account? = null,
    val rows: List<AccountTransactionRow> = emptyList(),
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val pendingDeleteTransactionId: Long? = null,
    val editingTransaction: Transaction? = null,
    val editingCategories: List<Category> = emptyList(),
    val editingAccounts: List<Account> = emptyList(),
    val errorMessage: String? = null
)
