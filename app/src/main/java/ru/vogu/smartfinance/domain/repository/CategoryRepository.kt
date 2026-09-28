package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.domain.model.Category
import java.math.BigDecimal

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeExpenseCategories(): Flow<List<Category>>
    fun observeIncomeCategories(): Flow<List<Category>>
    fun observeBudgeted(): Flow<List<Category>>
    suspend fun getAllOnce(): List<Category>
    suspend fun getById(id: Long): Category?
    suspend fun createCategory(category: Category): Long
    suspend fun updateCategory(category: Category)

    /** Системные категории (isSystem = true) удалить нельзя — репозиторий бросает [IllegalArgumentException]. */
    suspend fun deleteCategory(category: Category)

    /**
     * Устанавливает/снимает (null) месячный лимит бюджета — CLAUDE.md §2.5. В отличие от
     * [updateCategory] разрешён и для системных категорий (лимит — персональная настройка
     * пользователя, а не часть идентичности категории, на которую ссылается промпт Gemini).
     */
    suspend fun updateCategoryBudget(categoryId: Long, monthlyBudget: BigDecimal?)
}
