package ru.vogu.smartfinance.domain.model

import ru.vogu.smartfinance.core.common.Constants
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Агрегированные данные для Collapsible Dashboard Header — CLAUDE.md §5.1: баланс,
 * расходы за сегодня и текущий месяц с индикатором бюджета.
 */
data class DashboardSummary(
    val totalBalance: BigDecimal,
    val baseCurrency: String,
    val spentToday: BigDecimal,
    val spentThisMonth: BigDecimal,
    val monthlyBudgetTotal: BigDecimal?
) {
    /** Доля израсходованного месячного бюджета в процентах (0..100+); null — если ни у одной категории лимит не задан. */
    val budgetUsagePercent: Int?
        get() = monthlyBudgetTotal?.takeIf { it.signum() > 0 }?.let { budget ->
            spentThisMonth
                .divide(budget, 4, RoundingMode.HALF_EVEN)
                .multiply(BigDecimal(100))
                .toInt()
                .coerceAtLeast(0)
        }

    /** true, когда израсходовано 80%+ месячного бюджета — CLAUDE.md §2.5 (порог предупреждения). */
    val isBudgetWarning: Boolean
        get() = (budgetUsagePercent ?: 0) >= Constants.BUDGET_WARNING_THRESHOLD_PERCENT

    /** true, когда месячный бюджет уже превышен — CLAUDE.md §2.5. */
    val isBudgetExceeded: Boolean
        get() = (budgetUsagePercent ?: 0) >= Constants.BUDGET_EXCEEDED_THRESHOLD_PERCENT
}
