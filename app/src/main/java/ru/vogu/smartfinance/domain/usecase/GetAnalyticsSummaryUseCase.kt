package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.vogu.smartfinance.core.common.startOfDay
import ru.vogu.smartfinance.core.common.startOfMonth
import ru.vogu.smartfinance.core.common.startOfNextDay
import ru.vogu.smartfinance.core.common.startOfNextMonth
import ru.vogu.smartfinance.core.common.toLocalDate
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.AnalyticsPeriod
import ru.vogu.smartfinance.domain.model.AnalyticsSummary
import ru.vogu.smartfinance.domain.model.CategorySpending
import ru.vogu.smartfinance.domain.model.DailySpendingPoint
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val TOP_TRANSACTIONS_LIMIT = 5

/**
 * Данные экрана аналитики — CLAUDE.md §5.2: Pie Chart расходов по категориям, график
 * динамики по дням, список топ-трат. Границы периода вычисляются от "сегодня" в момент
 * подписки (тот же подход, что и [ObserveDashboardSummaryUseCase] — экран пересчитывает их
 * заново при каждом открытии, полноценный live-пересчёт на полуночи вне рамок ВКР-проекта).
 */
class GetAnalyticsSummaryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(
        period: AnalyticsPeriod,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<AnalyticsSummary> {
        val (fromInclusive, toExclusive) = rangeFor(period, now, zoneId)
        val periodStart = fromInclusive.toLocalDate(zoneId)
        // toExclusive — полночь ПОСЛЕ последнего дня периода, поэтому конец периода включительно на день раньше.
        val periodEndInclusive = toExclusive.toLocalDate(zoneId).minusDays(1)

        return combine(
            transactionRepository.observeByDateRange(fromInclusive, toExclusive),
            transactionRepository.observeSpendingByCategory(fromInclusive, toExclusive),
            transactionRepository.observeDailySpending(fromInclusive, toExclusive),
            categoryRepository.observeAll()
        ) { transactions, categoryAmounts, dailyRows, categories ->
            val categoriesById = categories.associateBy { it.id }

            val totalExpenses = transactions.sumAmountBase(TransactionType.EXPENSE)
            val totalIncome = transactions.sumAmountBase(TransactionType.INCOME)

            val categoryBreakdown = categoryAmounts
                .filter { it.totalAmountBase.signum() > 0 }
                .sortedByDescending { it.totalAmountBase }
                .map { row ->
                    CategorySpending(
                        category = row.categoryId?.let { categoriesById[it] },
                        amountBase = row.totalAmountBase,
                        percentage = row.totalAmountBase.percentOf(totalExpenses)
                    )
                }

            val dailySpending = fillDailyGaps(periodStart, periodEndInclusive, dailyRows)

            val topTransactions = transactions
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sortedByDescending { it.amountBase }
                .take(TOP_TRANSACTIONS_LIMIT)

            AnalyticsSummary(
                period = period,
                periodStart = periodStart,
                periodEndInclusive = periodEndInclusive,
                totalExpenses = totalExpenses,
                totalIncome = totalIncome,
                categoryBreakdown = categoryBreakdown,
                dailySpending = dailySpending,
                topTransactions = topTransactions
            )
        }
    }

    /** [Instant] "начало периода" (включительно) и "конец периода" (эксклюзивная верхняя граница). */
    private fun rangeFor(period: AnalyticsPeriod, now: Instant, zoneId: ZoneId): Pair<Instant, Instant> =
        when (period) {
            AnalyticsPeriod.WEEK -> {
                val todayStart = now.startOfDay(zoneId)
                val sixDaysAgo = todayStart.toLocalDate(zoneId).minusDays(6).atStartOfDay(zoneId).toInstant()
                sixDaysAgo to now.startOfNextDay(zoneId)
            }
            AnalyticsPeriod.MONTH -> now.startOfMonth(zoneId) to now.startOfNextMonth(zoneId)
            AnalyticsPeriod.YEAR -> {
                val yearStart = now.atZone(zoneId).toLocalDate().withDayOfYear(1).atStartOfDay(zoneId).toInstant()
                val nextYearStart = now.atZone(zoneId).toLocalDate().withDayOfYear(1).plusYears(1).atStartOfDay(zoneId).toInstant()
                yearStart to nextYearStart
            }
        }

    /** DAO отдаёт только дни с ненулевыми расходами — для непрерывного графика недостающие дни дозаполняются нулями. */
    private fun fillDailyGaps(
        from: LocalDate,
        toInclusive: LocalDate,
        rows: List<DailySpendingPoint>
    ): List<DailySpendingPoint> {
        val byDate = rows.associateBy { it.date }
        val result = mutableListOf<DailySpendingPoint>()
        var day = from
        while (!day.isAfter(toInclusive)) {
            result += byDate[day] ?: DailySpendingPoint(date = day, amountBase = BigDecimal.ZERO)
            day = day.plusDays(1)
        }
        return result
    }

    private fun List<Transaction>.sumAmountBase(type: TransactionType): BigDecimal =
        this.filter { it.transactionType == type }
            .fold(BigDecimal.ZERO) { sum, transaction -> sum + transaction.amountBase }

    private fun BigDecimal.percentOf(total: BigDecimal): Double {
        if (total.signum() <= 0) return 0.0
        return this.divide(total, 4, RoundingMode.HALF_EVEN).multiply(BigDecimal(100)).toDouble()
    }
}
