package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * "Своя категория" — CLAUDE.md §5.2 ("Экран настроек: управление категориями"). Создаётся
 * НЕ системной (isSystem = false, см. [ru.vogu.smartfinance.data.repository.CategoryRepositoryImpl] —
 * системные категории защищены от переименования/удаления, пользовательские — нет),
 * сортируется после всех существующих.
 */
class CreateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        name: String,
        iconName: String,
        colorHex: String,
        isIncomeCategory: Boolean
    ): Resource<Category> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Resource.Error("Укажите название категории.")
        }
        if (trimmedName.length > 40) {
            return Resource.Error("Название категории слишком длинное (максимум 40 символов).")
        }

        return try {
            val existing = categoryRepository.getAllOnce()
            if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                return Resource.Error("Категория с таким названием уже есть.")
            }

            val category = Category(
                id = 0L,
                name = trimmedName,
                iconName = iconName,
                colorHex = colorHex,
                monthlyBudget = null,
                isIncomeCategory = isIncomeCategory,
                isSystem = false,
                sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
            val id = categoryRepository.createCategory(category)
            Resource.Success(category.copy(id = id))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось создать категорию.", e)
        }
    }
}
