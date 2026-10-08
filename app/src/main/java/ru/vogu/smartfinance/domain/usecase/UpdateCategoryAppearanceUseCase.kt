package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * "Изменить иконку/цвет категории" — CLAUDE.md §5.2. Разрешено для любых категорий,
 * включая системные (см. [CategoryRepository.updateCategoryAppearance]).
 */
class UpdateCategoryAppearanceUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long, iconName: String, colorHex: String): Resource<Unit> = try {
        categoryRepository.updateCategoryAppearance(categoryId, iconName, colorHex)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Не удалось сохранить внешний вид категории.", e)
    }
}
