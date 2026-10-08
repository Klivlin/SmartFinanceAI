package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import javax.inject.Inject

/** Одна операция в списке экрана "Категории" — транзакция + разрешённое имя счёта списания. */
data class CategoryTransactionRow(
    val transaction: Transaction,
    val account: Account?
)

/**
 * Список всех операций конкретной категории с уже разрешённым счётом — CLAUDE.md §5.2
 * ("зайти в категорию и увидеть все траты по ней"). ViewModel не резолвит счета сама —
 * это бизнес-логика уровня UseCase (CLAUDE.md §9.3).
 */
class GetCategoryTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    operator fun invoke(categoryId: Long): Flow<List<CategoryTransactionRow>> =
        combine(
            transactionRepository.observeByCategory(categoryId),
            accountRepository.observeAll()
        ) { transactions, accounts ->
            val accountsById = accounts.associateBy { it.id }
            transactions.map { transaction ->
                CategoryTransactionRow(transaction = transaction, account = accountsById[transaction.accountId])
            }
        }
}
