package ru.vogu.smartfinance.domain.usecase

import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import java.math.BigDecimal
import javax.inject.Inject

/**
 * "Установить лимит" / "Изменить лимит" / "Снять лимит" на экране бюджета — CLAUDE.md §2.5.
 * [monthlyBudget] == null снимает лимит с категории (прогресс-бар скрывается, предупреждения
 * не показываются — см. [ru.vogu.smartfinance.domain.model.CategoryBudgetStatus]).
 */
class UpdateCategoryBudgetUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long, monthlyBudget: BigDecimal?): Resource<Unit> {
        if (monthlyBudget != null && monthlyBudget.signum() <= 0) {
            return Resource.Error("Лимит бюджета должен быть больше нуля.")
        }
        return try {
            categoryRepository.updateCategoryBudget(categoryId, monthlyBudget)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Не удалось сохранить лимит бюджета.", e)
        }
    }
}
