package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.CategoryAmount
import ru.vogu.smartfinance.domain.model.DailySpendingPoint
import ru.vogu.smartfinance.domain.model.SplitItem
import ru.vogu.smartfinance.domain.model.Transaction
import java.math.BigDecimal
import java.time.Instant

interface TransactionRepository {

    fun observeRecent(limit: Int = 50): Flow<List<Transaction>>
    fun observeByAccount(accountId: Long): Flow<List<Transaction>>
    fun observeByDateRange(fromInclusive: Instant, toExclusive: Instant): Flow<List<Transaction>>
    suspend fun getById(id: Long): Transaction?

    /** Сумма расходов по категории за период — Pie Chart аналитики и прогресс-бары бюджета (CLAUDE.md §2.5/§5.2). */
    fun observeSpendingByCategory(fromInclusive: Instant, toExclusive: Instant): Flow<List<CategoryAmount>>

    /** Динамика расходов по дням за период — график в аналитике (CLAUDE.md §5.2). */
    fun observeDailySpending(fromInclusive: Instant, toExclusive: Instant): Flow<List<DailySpendingPoint>>

    /**
     * Создаёт транзакцию и атомарно (в одной Room-транзакции) обновляет баланс(ы) затронутых
     * счетов. Для TRANSFER обязателен [toAccountId]; для остальных типов игнорируется.
     */
    suspend fun createTransaction(
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long?,
        amount: BigDecimal,
        transactionType: TransactionType,
        title: String,
        notes: String? = null,
        timestamp: Instant = Instant.now(),
        source: TransactionSource,
        rawInputText: String? = null,
        originalCurrency: String? = null,
        transferFeeAmount: BigDecimal? = null,
        recurringRuleId: Long? = null,
        splitItems: List<SplitItem> = emptyList()
    ): Transaction

    /** Undo — CLAUDE.md §5.1 "Отменить": откатывает баланс и удаляет запись. */
    suspend fun deleteTransaction(transactionId: Long)

    suspend fun updateTransaction(transaction: Transaction)
}
