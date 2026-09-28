package ru.vogu.smartfinance.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Период агрегации для экрана аналитики — CLAUDE.md §5.2. Границы вычисляются в
 * [ru.vogu.smartfinance.domain.usecase.GetAnalyticsSummaryUseCase] относительно "сегодня".
 */
enum class AnalyticsPeriod {
    WEEK,
    MONTH,
    YEAR
}

/** Сырая сумма расходов по категории за период — промежуточная проекция между DAO и domain-агрегатами. */
data class CategoryAmount(
    val categoryId: Long?,
    val totalAmountBase: BigDecimal
)

/** Точка графика динамики расходов по дням — CLAUDE.md §5.2 ("график динамики по дням"). */
data class DailySpendingPoint(
    val date: LocalDate,
    val amountBase: BigDecimal
)

/**
 * Доля расходов по одной категории за период — источник для Pie Chart и списка
 * "топ трат по категориям" (CLAUDE.md §5.2). [category] == null агрегирует операции без
 * категории (удалённая категория — CategoryEntity ссылка со SET_NULL, см. Entities.kt).
 */
data class CategorySpending(
    val category: Category?,
    val amountBase: BigDecimal,
    val percentage: Double
)

/** Агрегированные данные экрана аналитики за выбранный [period] — CLAUDE.md §5.2. */
data class AnalyticsSummary(
    val period: AnalyticsPeriod,
    val periodStart: LocalDate,
    val periodEndInclusive: LocalDate,
    val totalExpenses: BigDecimal,
    val totalIncome: BigDecimal,
    val categoryBreakdown: List<CategorySpending>,
    val dailySpending: List<DailySpendingPoint>,
    val topTransactions: List<Transaction>
)
