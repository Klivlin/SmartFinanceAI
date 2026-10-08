package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import javax.inject.Inject

enum class ReorderDirection { UP, DOWN }

/**
 * "Поднять"/"Опустить" категорию в списке — CLAUDE.md §5.2. Работает одинаково для
 * системных (id 1..9) и собственных категорий: порядок — персональная настройка, а не
 * часть идентичности категории, на которую ссылается промпт Gemini (в отличие от имени).
 * Меняет местами `sortOrder` текущей категории и соседней по списку.
 */
class ReorderCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        categoryId: Long,
        direction: ReorderDirection,
        orderedCategories: List<Category>
    ): Resource<Unit> {
        val index = orderedCategories.indexOfFirst { it.id == categoryId }
        if (index < 0) return Resource.Error("Категория не найдена.")

        val targetIndex = if (direction == ReorderDirection.UP) index - 1 else index + 1
        if (targetIndex !in orderedCategories.indices) {
            // Уже первая/последняя — тихо ничего не делаем, а не ошибка.
            return Resource.Success(Unit)
        }

        val current = orderedCategories[index]
        val target = orderedCategories[targetIndex]

        return try {
            categoryRepository.updateCategorySortOrder(current.id, target.sortOrder)
            categoryRepository.updateCategorySortOrder(target.id, current.sortOrder)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось изменить порядок категорий.", e)
        }
    }
}
