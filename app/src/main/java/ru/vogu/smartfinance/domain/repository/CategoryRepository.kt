package ru.vogu.smartfinance.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.vogu.smartfinance.domain.model.Category

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
}
