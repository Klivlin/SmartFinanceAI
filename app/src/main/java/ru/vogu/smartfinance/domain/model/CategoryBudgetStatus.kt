package ru.vogu.smartfinance.domain.model

import java.math.BigDecimal

/**
 * Статус месячного лимита по одной категории расходов — CLAUDE.md §2.5/§5.1. [usagePercent],
 * [isWarning] и [isExceeded] — null/false, если у категории лимит не задан (карточка в UI
 * предлагает установить его вместо прогресс-бара). Пороги 80%/100% зафиксированы в
 * [ru.vogu.smartfinance.core.common.Constants].
 */
data class CategoryBudgetStatus(
    val category: Category,
    val spentThisMonth: BigDecimal,
    val usagePercent: Int?,
    val isWarning: Boolean,
    val isExceeded: Boolean
)
