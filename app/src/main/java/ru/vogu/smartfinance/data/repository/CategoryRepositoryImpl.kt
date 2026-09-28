package ru.vogu.smartfinance.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.data.mapper.toDomain
import ru.vogu.smartfinance.data.mapper.toEntity
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import java.math.BigDecimal
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeExpenseCategories(): Flow<List<Category>> =
        categoryDao.observeExpenseCategories().map { list -> list.map { it.toDomain() } }

    override fun observeIncomeCategories(): Flow<List<Category>> =
        categoryDao.observeIncomeCategories().map { list -> list.map { it.toDomain() } }

    override fun observeBudgeted(): Flow<List<Category>> =
        categoryDao.observeBudgeted().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllOnce(): List<Category> = categoryDao.getAllOnce().map { it.toDomain() }

    override suspend fun getById(id: Long): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun createCategory(category: Category): Long = categoryDao.insert(category.toEntity())

    override suspend fun updateCategory(category: Category) {
        require(!category.isSystem) { "Системные категории нельзя изменять." }
        categoryDao.update(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        require(!category.isSystem) {
            "Системная категория \"${category.name}\" не может быть удалена — на её id ссылается промпт Gemini."
        }
        categoryDao.delete(category.toEntity())
    }

    override suspend fun updateCategoryBudget(categoryId: Long, monthlyBudget: BigDecimal?) {
        require(monthlyBudget == null || monthlyBudget.signum() > 0) {
            "Лимит бюджета должен быть положительным (или null, чтобы снять лимит)."
        }
        categoryDao.updateBudget(categoryId, monthlyBudget)
    }
}
