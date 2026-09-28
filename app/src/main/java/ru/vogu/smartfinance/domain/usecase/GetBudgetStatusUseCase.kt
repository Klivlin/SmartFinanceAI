package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.common.startOfMonth
import ru.vogu.smartfinance.core.common.startOfNextMonth
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject

/**
 * Статус месячных лимитов бюджета по категориям расходов — CLAUDE.md §2.5. Отдаёт ВСЕ
 * категории расходов (не только те, у кого лимит уже задан), чтобы экран бюджета мог
 * предложить "Установить лимит" для остальных — CLAUDE.md §5.2 ("Экран настроек: управление
 * категориями"), здесь же — прямой путь для управления лимитами конкретно.
 */
class GetBudgetStatusUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(now: Instant = Instant.now()): Flow<List<CategoryBudgetStatus>> {
        val monthStart = now.startOfMonth()
        val monthEnd = now.startOfNextMonth()

        return combine(
            categoryRepository.observeExpenseCategories(),
            transactionRepository.observeSpendingByCategory(monthStart, monthEnd)
        ) { categories, spendingRows ->
            val spentByCategoryId = spendingRows
                .mapNotNull { row -> row.categoryId?.let { it to row.totalAmountBase } }
                .toMap()

            categories
                .sortedWith(compareByDescending<Category> { it.monthlyBudget != null }.thenBy { it.sortOrder })
                .map { category ->
                    val spent = spentByCategoryId[category.id] ?: BigDecimal.ZERO
                    val budget = category.monthlyBudget
                    val usagePercent = budget?.takeIf { it.signum() > 0 }?.let {
                        spent.divide(it, 4, RoundingMode.HALF_EVEN)
                            .multiply(BigDecimal(100))
                            .toInt()
                            .coerceAtLeast(0)
                    }

                    CategoryBudgetStatus(
                        category = category,
                        spentThisMonth = spent,
                        usagePercent = usagePercent,
                        isWarning = (usagePercent ?: 0) >= Constants.BUDGET_WARNING_THRESHOLD_PERCENT,
                        isExceeded = (usagePercent ?: 0) >= Constants.BUDGET_EXCEEDED_THRESHOLD_PERCENT
                    )
                }
        }
    }
}
