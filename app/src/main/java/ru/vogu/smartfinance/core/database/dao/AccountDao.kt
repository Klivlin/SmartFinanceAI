package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.AccountEntity
import java.math.BigDecimal

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, id ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, id ASC")
    suspend fun getActiveOnce(): List<AccountEntity>

    /** Атомарно изменяет баланс на [delta] (может быть отрицательным). Используется репозиторием в транзакции Room. */
    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, delta: BigDecimal)

    @Query("SELECT COUNT(*) FROM accounts WHERE is_archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE accounts SET is_archived = 1 WHERE id = :accountId")
    suspend fun archive(accountId: Long)

    /** Счёт, свёрнуто отображаемый на главном Chat-экране (CLAUDE.md §5.1). */
    @Query("SELECT * FROM accounts WHERE is_primary = 1 AND is_archived = 0 LIMIT 1")
    fun observePrimary(): Flow<AccountEntity?>

    @Query("UPDATE accounts SET is_primary = 0 WHERE is_primary = 1")
    suspend fun clearPrimary()

    @Query("UPDATE accounts SET is_primary = 1 WHERE id = :accountId")
    suspend fun markPrimary(accountId: Long)

    /** Атомарно переносит флаг "основной" на [accountId] — инвариант "не более одного основного счёта". */
    @Transaction
    suspend fun setPrimary(accountId: Long) {
        clearPrimary()
        markPrimary(accountId)
    }
}
