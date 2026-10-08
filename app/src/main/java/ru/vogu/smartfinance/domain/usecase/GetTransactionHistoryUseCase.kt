package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import javax.inject.Inject

/** Одна операция в общей истории — CLAUDE.md список доработок, п.2 ("история как в банковских приложениях"). */
data class HistoryRow(
    val transaction: Transaction,
    val account: Account?,
    val category: Category?
)

/**
 * Полная история операций (не только последние N, как в ленте чата) с уже разрешёнными
 * счётом и категорией — аналог [GetCategoryTransactionsUseCase], но без фильтра по категории.
 * ViewModel не резолвит счета/категории сама — бизнес-логика уровня UseCase (CLAUDE.md §9.3).
 */
class GetTransactionHistoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(): Flow<List<HistoryRow>> =
        combine(
            transactionRepository.observeRecent(limit = Int.MAX_VALUE),
            accountRepository.observeAll(),
            categoryRepository.observeAll()
        ) { transactions, accounts, categories ->
            val accountsById = accounts.associateBy { it.id }
            val categoriesById = categories.associateBy { it.id }
            transactions.map { transaction ->
                HistoryRow(
                    transaction = transaction,
                    account = accountsById[transaction.accountId],
                    category = transaction.categoryId?.let { categoriesById[it] }
                )
            }
        }
}
