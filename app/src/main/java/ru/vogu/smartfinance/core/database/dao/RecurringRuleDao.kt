package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.RecurringRuleEntity
import java.time.LocalDate

@Dao
interface RecurringRuleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules WHERE is_active = 1 ORDER BY next_execution_date ASC")
    fun observeActive(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY next_execution_date ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    /** Правила, по которым пора создать транзакцию и/или показать напоминание (Calendar/WorkManager — Шаг 6). */
    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE is_active = 1 AND next_execution_date <= :onOrBefore
        ORDER BY next_execution_date ASC
        """
    )
    suspend fun getDue(onOrBefore: LocalDate): List<RecurringRuleEntity>

    @Query(
        """
        UPDATE recurring_rules
        SET last_executed_date = :executedOn, next_execution_date = :nextExecutionDate
        WHERE id = :id
        """
    )
    suspend fun markExecuted(id: Long, executedOn: LocalDate, nextExecutionDate: LocalDate)
}
