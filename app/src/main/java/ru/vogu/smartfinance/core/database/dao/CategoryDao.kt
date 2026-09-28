package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    /** Системные категории (isSystem = true, id 1..9) запрещено удалять на уровне репозитория/UseCase. */
    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE is_income_category = 0 ORDER BY sort_order ASC, id ASC")
    fun observeExpenseCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_income_category = 1 ORDER BY sort_order ASC, id ASC")
    fun observeIncomeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE monthly_budget IS NOT NULL ORDER BY sort_order ASC")
    fun observeBudgeted(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?
}
