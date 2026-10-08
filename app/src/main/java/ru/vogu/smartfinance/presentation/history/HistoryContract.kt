package ru.vogu.smartfinance.presentation.history

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.usecase.HistoryRow

/**
 * "История операций" — CLAUDE.md список доработок, п.2: полная лента расходов/доходов в
 * духе банковских приложений (сгруппирована по дням), а не только последние N в чате.
 * Правка/удаление переиспользуют тот же BottomSheet-редактор и [ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase],
 * что и на карточках чата/категорий.
 */
sealed interface HistoryIntent {
    data class FilterChanged(val filter: TransactionType?) : HistoryIntent
    data class EditRequested(val transactionId: Long) : HistoryIntent
    data object DismissEdit : HistoryIntent
    data class SaveEditedTransaction(val transaction: Transaction) : HistoryIntent
    data class DeleteRequested(val transactionId: Long) : HistoryIntent
    data object DeleteDismissed : HistoryIntent
    data object DeleteConfirmed : HistoryIntent
    data object ErrorMessageShown : HistoryIntent
}

data class HistoryState(
    val rows: List<HistoryRow> = emptyList(),
    val filter: TransactionType? = null,
    val baseCurrency: String = Constants.DEFAULT_BASE_CURRENCY,
    val isLoading: Boolean = true,
    val editingTransaction: Transaction? = null,
    val editingCategories: List<Category> = emptyList(),
    val editingAccounts: List<Account> = emptyList(),
    val pendingDeleteTransactionId: Long? = null,
    val errorMessage: String? = null
) {
    val filteredRows: List<HistoryRow>
        get() = if (filter == null) rows else rows.filter { it.transaction.transactionType == filter }
}
