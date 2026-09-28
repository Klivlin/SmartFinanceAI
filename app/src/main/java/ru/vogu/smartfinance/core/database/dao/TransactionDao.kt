package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.SplitItemEntity
import ru.vogu.smartfinance.core.database.entity.TransactionEntity
import ru.vogu.smartfinance.core.database.entity.TransactionType
import java.math.BigDecimal
import java.time.Instant

/** Проекция для агрегации расходов по категории (аналитика, бюджет). */
data class CategorySpendingRow(
    val categoryId: Long?,
    val totalAmountBase: BigDecimal
)

/** Проекция для графика динамики по дням (аналитика §5.2). */
data class DailySpendingRow(
    val dayEpochDay: Long,
    val totalAmountBase: BigDecimal
)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSplitItems(items: List<SplitItemEntity>): List<Long>

    /** Вставляет транзакцию и (опционально) её сплит-позиции одной атомарной операцией — CLAUDE.md §2.3. */
    @Transaction
    suspend fun insertWithSplits(transaction: TransactionEntity, splitItems: List<SplitItemEntity>): Long {
        val transactionId = insert(transaction)
        if (splitItems.isNotEmpty()) {
            insertSplitItems(splitItems.map { it.copy(transactionId = transactionId) })
        }
        return transactionId
    }

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM split_items WHERE transaction_id = :transactionId")
    suspend fun getSplitItems(transactionId: Long): List<SplitItemEntity>

    /** Лента для главного Chat-экрана — самые новые сверху. */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId OR to_account_id = :accountId ORDER BY timestamp DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :fromInclusive AND timestamp < :toExclusive
        ORDER BY timestamp DESC
        """
    )
    fun observeByDateRange(fromInclusive: Instant, toExclusive: Instant): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :fromInclusive AND timestamp < :toExclusive
        ORDER BY timestamp DESC
        """
    )
    suspend fun getByDateRangeOnce(fromInclusive: Instant, toExclusive: Instant): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId ORDER BY timestamp DESC")
    fun observeByCategory(categoryId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE recurring_rule_id = :recurringRuleId ORDER BY timestamp DESC")
    fun observeByRecurringRule(recurringRuleId: Long): Flow<List<TransactionEntity>>

    /** Сумма расходов по категории за период — для прогресс-бара лимита бюджета (CLAUDE.md §2.5). */
    @Query(
        """
        SELECT category_id AS categoryId, COALESCE(SUM(amount_base), 0) AS totalAmountBase
        FROM transactions
        WHERE transaction_type = 'EXPENSE'
          AND timestamp >= :fromInclusive AND timestamp < :toExclusive
        GROUP BY category_id
        """
    )
    fun observeSpendingByCategory(fromInclusive: Instant, toExclusive: Instant): Flow<List<CategorySpendingRow>>

    @Query(
        """
        SELECT CAST(timestamp / 86400000 AS INTEGER) AS dayEpochDay,
               COALESCE(SUM(amount_base), 0) AS totalAmountBase
        FROM transactions
        WHERE transaction_type = 'EXPENSE'
          AND timestamp >= :fromInclusive AND timestamp < :toExclusive
        GROUP BY dayEpochDay
        ORDER BY dayEpochDay ASC
        """
    )
    fun observeDailySpending(fromInclusive: Instant, toExclusive: Instant): Flow<List<DailySpendingRow>>

    @Query(
        """
        SELECT COALESCE(SUM(amount_base), 0) FROM transactions
        WHERE transaction_type = :type AND timestamp >= :fromInclusive AND timestamp < :toExclusive
        """
    )
    fun observeTotalByType(
        type: TransactionType,
        fromInclusive: Instant,
        toExclusive: Instant
    ): Flow<BigDecimal>

    @Query(
        """
        SELECT * FROM transactions
        WHERE title LIKE '%' || :query || '%' OR raw_input_text LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 100): List<TransactionEntity>

    /** Полная выгрузка для экспорта в CSV/Excel/JSON — CLAUDE.md §2.7. */
    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<TransactionEntity>
}
