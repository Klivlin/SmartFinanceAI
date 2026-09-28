package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.SplitItemEntity

@Dao
interface SplitItemDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<SplitItemEntity>): List<Long>

    @Delete
    suspend fun delete(item: SplitItemEntity)

    @Query("SELECT * FROM split_items WHERE transaction_id = :transactionId")
    fun observeByTransaction(transactionId: Long): Flow<List<SplitItemEntity>>

    @Query("DELETE FROM split_items WHERE transaction_id = :transactionId")
    suspend fun deleteByTransactionId(transactionId: Long)
}
