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

    /** Полная история для экрана "История"/экспорта — сортировка по фактической дате операции. */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TransactionEntity>>

    /**
     * Лента для главного Chat-экрана — сортировка по МОМЕНТУ ВВОДА (created_at), а не по
     * дате самой операции (timestamp). Это разные вещи: чат — это разговор, его порядок
     * определяется тем, КОГДА пользователь что-то написал, а не тем, к какой дате относится
     * траты. Раньше обе ленты сортировались по timestamp — операция "вчера такси 500", только
     * что введённая ПРЯМО СЕЙЧАС, вставала выше сегодняшних сообщений и визуально терялась
     * (автоскролл в конец ленты её не показывал), хотя reserve создавалась корректно.
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecentByEntryOrder(limit: Int = 50): Flow<List<TransactionEntity>>

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

    /** Для ежедневного напоминания "внести расходы" — CLAUDE.md §2.4/§5.1: есть ли уже операции сегодня. */
    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :fromInclusive AND timestamp < :toExclusive")
    suspend fun countByDateRange(fromInclusive: Instant, toExclusive: Instant): Int

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

    /** Сумма доходов по категории за период — для вкладки "Доходы" на экране "Категории". */
    @Query(
        """
        SELECT category_id AS categoryId, COALESCE(SUM(amount_base), 0) AS totalAmountBase
        FROM transactions
        WHERE transaction_type = 'INCOME'
          AND timestamp >= :fromInclusive AND timestamp < :toExclusive
        GROUP BY category_id
        """
    )
    fun observeIncomeByCategory(fromInclusive: Instant, toExclusive: Instant): Flow<List<CategorySpendingRow>>

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
