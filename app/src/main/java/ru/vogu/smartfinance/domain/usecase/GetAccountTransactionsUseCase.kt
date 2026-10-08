package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Одна операция в списке экрана "Все операции счёта" — CLAUDE.md §5.2 (тап по счёту → его
 * расходы/доходы). [signedAmount]/[isIncoming] уже посчитаны ОТНОСИТЕЛЬНО просматриваемого
 * счёта: для TRANSFER, где счёт — получатель, это [Transaction.toAmount] (сумма в ЕГО валюте
 * по реальному курсу), а не исходная [Transaction.amount] в валюте счёта-отправителя.
 */
data class AccountTransactionRow(
    val transaction: Transaction,
    val category: Category?,
    val signedAmount: BigDecimal,
    val isIncoming: Boolean
)

/**
 * Список всех операций конкретного счёта (расход/доход/перевод в обе стороны) — по аналогии с
 * [GetCategoryTransactionsUseCase], но для счетов. [ru.vogu.smartfinance.domain.repository.TransactionRepository.observeByAccount]
 * уже отдаёт операции, где счёт — источник ИЛИ получатель перевода.
 */
class GetAccountTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(accountId: Long): Flow<List<AccountTransactionRow>> =
        combine(
            transactionRepository.observeByAccount(accountId),
            categoryRepository.observeAll()
        ) { transactions, categories ->
            val categoriesById = categories.associateBy { it.id }
            transactions.map { transaction -> transaction.toRowFor(accountId, categoriesById) }
        }

    private fun Transaction.toRowFor(accountId: Long, categoriesById: Map<Long, Category>): AccountTransactionRow {
        val isIncoming = when (transactionType) {
            TransactionType.EXPENSE -> false
            TransactionType.INCOME, TransactionType.ADJUSTMENT -> true
            TransactionType.TRANSFER -> toAccountId == accountId
        }
        val displayAmount = if (transactionType == TransactionType.TRANSFER && toAccountId == accountId) {
            toAmount ?: amount
        } else {
            amount
        }
        return AccountTransactionRow(
            transaction = this,
            category = categoryId?.let { categoriesById[it] },
            signedAmount = if (isIncoming) displayAmount else displayAmount.negate(),
            isIncoming = isIncoming
        )
    }
}
