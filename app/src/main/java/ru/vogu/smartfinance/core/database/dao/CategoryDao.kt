package ru.vogu.smartfinance.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.core.database.entity.CategoryEntity
import java.math.BigDecimal

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

    /**
     * Отдельный от [update] метод: меняет только месячный лимит бюджета (CLAUDE.md §2.5) и
     * намеренно обходит запрет на изменение системных категорий — лимит бюджета настраивается
     * пользователем даже для предустановленных категорий вроде "Кафе" (см. пример в CLAUDE.md
     * §2.5), в отличие от их имени/иконки, на которые ссылается промпт Gemini (CLAUDE.md §3.3).
     */
    @Query("UPDATE categories SET monthly_budget = :monthlyBudget WHERE id = :categoryId")
    suspend fun updateBudget(categoryId: Long, monthlyBudget: BigDecimal?)

    /**
     * Отдельный от [update] метод, как и [updateBudget]: разрешает менять иконку/цвет даже
     * у системных категорий (id 1..9). В отличие от имени, они не влияют на промпт Gemini
     * (CLAUDE.md §3.3 ссылается на категории по id/имени, не по цвету/иконке) — это чисто
     * визуальная персонализация (CLAUDE.md §5.2 "Управление категориями").
     */
    @Query("UPDATE categories SET icon_name = :iconName, color_hex = :colorHex WHERE id = :categoryId")
    suspend fun updateAppearance(categoryId: Long, iconName: String, colorHex: String)

    /**
     * Отдельный от [update] метод, как [updateBudget]/[updateAppearance]: порядок в списке —
     * персональная настройка, разрешена и для системных категорий, и для собственных
     * (CLAUDE.md §5.2 "Управление категориями").
     */
    @Query("UPDATE categories SET sort_order = :sortOrder WHERE id = :categoryId")
    suspend fun updateSortOrder(categoryId: Long, sortOrder: Int)
}
