package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.core.common.startOfMonth
import ru.vogu.smartfinance.core.common.startOfNextMonth
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

/**
 * Аналог [GetBudgetStatusUseCase] для категорий дохода — CLAUDE.md список доработок, п.5
 * ("добавить в категории доходы, чтобы получились расходы и доходы на странице"). Лимиты
 * бюджета к доходам не применимы, поэтому [CategoryBudgetStatus.usagePercent] всегда null —
 * переиспользуем ту же модель, чтобы UI экрана "Категории" не дублировался под каждую вкладку.
 */
class GetIncomeCategoryStatusUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(now: Instant = Instant.now()): Flow<List<CategoryBudgetStatus>> {
        val monthStart = now.startOfMonth()
        val monthEnd = now.startOfNextMonth()

        return combine(
            categoryRepository.observeIncomeCategories(),
            transactionRepository.observeIncomeByCategory(monthStart, monthEnd)
        ) { categories, incomeRows ->
            val receivedByCategoryId = incomeRows
                .mapNotNull { row -> row.categoryId?.let { it to row.totalAmountBase } }
                .toMap()

            categories
                .sortedBy { it.sortOrder }
                .map { category ->
                    CategoryBudgetStatus(
                        category = category,
                        spentThisMonth = receivedByCategoryId[category.id] ?: BigDecimal.ZERO,
                        usagePercent = null,
                        isWarning = false,
                        isExceeded = false
                    )
                }
        }
    }
}
