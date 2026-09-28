package ru.vogu.smartfinance.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.core.database.AppDatabase
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.TransactionDao
import ru.vogu.smartfinance.core.database.entity.TransactionEntity
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.data.mapper.toDomain
import ru.vogu.smartfinance.data.mapper.toEntity
import ru.vogu.smartfinance.domain.model.CategoryAmount
import ru.vogu.smartfinance.domain.model.DailySpendingPoint
import ru.vogu.smartfinance.domain.model.SplitItem
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) : TransactionRepository {

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeByAccount(accountId: Long): Flow<List<Transaction>> =
        transactionDao.observeByAccount(accountId).map { list -> list.map { it.toDomain() } }

    override fun observeByDateRange(fromInclusive: Instant, toExclusive: Instant): Flow<List<Transaction>> =
        transactionDao.observeByDateRange(fromInclusive, toExclusive).map { list -> list.map { it.toDomain() } }

    override fun observeSpendingByCategory(fromInclusive: Instant, toExclusive: Instant): Flow<List<CategoryAmount>> =
        transactionDao.observeSpendingByCategory(fromInclusive, toExclusive).map { rows ->
            rows.map { CategoryAmount(categoryId = it.categoryId, totalAmountBase = it.totalAmountBase) }
        }

    override fun observeDailySpending(fromInclusive: Instant, toExclusive: Instant): Flow<List<DailySpendingPoint>> =
        transactionDao.observeDailySpending(fromInclusive, toExclusive).map { rows ->
            rows.map { DailySpendingPoint(date = LocalDate.ofEpochDay(it.dayEpochDay), amountBase = it.totalAmountBase) }
        }

    override suspend fun getById(id: Long): Transaction? {
        val entity = transactionDao.getById(id) ?: return null
        val splits = transactionDao.getSplitItems(id).map { it.toDomain() }
        return entity.toDomain(splits)
    }

    override suspend fun createTransaction(
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        amount: BigDecimal,
        transactionType: TransactionType,
        title: String,
        notes: String?,
        timestamp: Instant,
        source: TransactionSource,
        rawInputText: String?,
        originalCurrency: String?,
        transferFeeAmount: BigDecimal?,
        recurringRuleId: Long?,
        splitItems: List<SplitItem>
    ): Transaction {
        require(amount.signum() > 0) { "Сумма транзакции должна быть положительной." }
        if (transactionType == TransactionType.TRANSFER) {
            requireNotNull(toAccountId) { "Для TRANSFER обязателен счёт-получатель (toAccountId)." }
            require(toAccountId != accountId) { "Счёт списания и счёт зачисления не могут совпадать." }
        }

        return appDatabase.withTransaction {
            val account = accountDao.getById(accountId)
                ?: error("Счёт id=$accountId не найден.")

            val entity = TransactionEntity(
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                amount = amount,
                originalCurrency = originalCurrency ?: account.currency,
                exchangeRate = BigDecimal.ONE, // TODO: конвертация по реальному курсу — вне рамок Шага 3
                amountBase = amount,
                transactionType = transactionType,
                title = title,
                notes = notes,
                timestamp = timestamp,
                source = source,
                rawInputText = rawInputText,
                transferFeeAmount = transferFeeAmount,
                recurringRuleId = recurringRuleId,
                isSplit = splitItems.isNotEmpty()
            )

            val transactionId = transactionDao.insertWithSplits(
                transaction = entity,
                splitItems = splitItems.map { it.toEntity() }
            )

            applyBalanceDelta(accountId, toAccountId, amount, transferFeeAmount, transactionType)

            val savedEntity = transactionDao.getById(transactionId) ?: error("Не удалось прочитать только что созданную транзакцию.")
            val savedSplits = transactionDao.getSplitItems(transactionId).map { it.toDomain() }
            savedEntity.toDomain(savedSplits)
        }
    }

    override suspend fun deleteTransaction(transactionId: Long) {
        appDatabase.withTransaction {
            val entity = transactionDao.getById(transactionId) ?: return@withTransaction
            // Откатываем баланс операцией, обратной исходной.
            applyBalanceDelta(
                accountId = entity.accountId,
                toAccountId = entity.toAccountId,
                amount = entity.amount,
                transferFeeAmount = entity.transferFeeAmount,
                transactionType = entity.transactionType,
                reverse = true
            )
            transactionDao.deleteById(transactionId)
        }
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        appDatabase.withTransaction {
            val previous = transactionDao.getById(transaction.id)
                ?: error("Транзакция id=${transaction.id} не найдена.")

            // Откатываем эффект старой версии, затем применяем эффект новой — так правки
            // суммы/счёта/типа через BottomSheet ("Изменить", CLAUDE.md §5.1) остаются согласованными.
            applyBalanceDelta(
                accountId = previous.accountId,
                toAccountId = previous.toAccountId,
                amount = previous.amount,
                transferFeeAmount = previous.transferFeeAmount,
                transactionType = previous.transactionType,
                reverse = true
            )
            applyBalanceDelta(
                accountId = transaction.accountId,
                toAccountId = transaction.toAccountId,
                amount = transaction.amount,
                transferFeeAmount = transaction.transferFeeAmount,
                transactionType = transaction.transactionType,
                reverse = false
            )
            transactionDao.update(transaction.copy(updatedAt = Instant.now()).toEntity())
        }
    }

    /** [reverse] = true откатывает эффект (используется при Undo/редактировании). */
    private suspend fun applyBalanceDelta(
        accountId: Long,
        toAccountId: Long?,
        amount: BigDecimal,
        transferFeeAmount: BigDecimal?,
        transactionType: TransactionType,
        reverse: Boolean = false
    ) {
        val sign = if (reverse) BigDecimal.valueOf(-1) else BigDecimal.ONE
        when (transactionType) {
            TransactionType.EXPENSE -> accountDao.adjustBalance(accountId, amount.negate().multiply(sign))
            TransactionType.INCOME -> accountDao.adjustBalance(accountId, amount.multiply(sign))
            TransactionType.TRANSFER -> {
                val fee = transferFeeAmount ?: BigDecimal.ZERO
                accountDao.adjustBalance(accountId, amount.add(fee).negate().multiply(sign))
                if (toAccountId != null) {
                    accountDao.adjustBalance(toAccountId, amount.multiply(sign))
                }
            }
            TransactionType.ADJUSTMENT -> accountDao.adjustBalance(accountId, amount.multiply(sign))
        }
    }
}
