package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.first
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Прогноз расходов по одной категории на следующий месяц — среднее за проанализированные месяцы. */
data class CategoryForecast(
    val categoryId: Long?,
    val categoryName: String,
    val predictedAmount: BigDecimal
)

/** Прогноз на один будущий месяц — расход по категориям, доход и то, что получится отложить. */
data class MonthForecast(
    val yearMonth: YearMonth,
    val categoryForecasts: List<CategoryForecast>,
    val predictedIncome: BigDecimal
) {
    val predictedExpense: BigDecimal get() = categoryForecasts.fold(BigDecimal.ZERO) { sum, c -> sum + c.predictedAmount }
    val predictedSavings: BigDecimal get() = predictedIncome - predictedExpense
}

/**
 * Прогноз трат/доходов на ближайшие месяцы — CLAUDE.md §5.2 ("расчёт предикт трат на будущий
 * месяц"). ВАЖНО: это НЕ калькулятор накопительного счёта (см. отдельный
 * [ru.vogu.smartfinance.core.ui.components.SavingsProjectionBottomSheet], который считает
 * начисление процентов на savings-счёт) — здесь анализируются обычные операции за последние
 * завершённые месяцы, ПО КАЖДОЙ КАТЕГОРИИ, где вообще были траты, а не одной обезличенной
 * суммой — иначе прогноз ничего не говорит о том, где реально можно сократить расходы. Те же
 * цифры использует [GenerateFinancialTipsUseCase] для советов (какая категория вырастет больше
 * всего, что сократить).
 *
 * Метод — среднемесячная трата по каждой категории за последние [monthsToAverage] завершённых
 * месяцев (месяцы без этой категории считаются нулём — это честное "в среднем в месяц", а не
 * средняя ТОЛЬКО по месяцам, где категория встретилась). Доход — то же среднее, но одной суммой
 * (разбивка дохода по категориям малополезна для большинства пользователей). Полностью локально
 * (0 токенов) — CLAUDE.md §3: прогноз не требует "понимания" текста, только арифметики.
 */
class GetSpendingForecastUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        monthsToAverage: Int = 3,
        monthsAhead: Int = 3,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<MonthForecast> {
        val today = now.atZone(zoneId).toLocalDate()

        // Транзакции по каждому из последних monthsToAverage ЗАВЕРШЁННЫХ месяцев.
        val completedMonths = (monthsToAverage downTo 1).map { monthsAgo ->
            val monthStart = today.withDayOfMonth(1).minusMonths(monthsAgo.toLong())
            val monthEnd = monthStart.plusMonths(1)
            transactionRepository.observeByDateRange(
                monthStart.toInstantAtStart(zoneId),
                monthEnd.toInstantAtStart(zoneId)
            ).first()
        }
        val hasCompletedMonthData = completedMonths.any { it.isNotEmpty() }

        // monthsForAverage — набор месяцев, по которым усредняем: завершённые, если они есть,
        // иначе единственный текущий (ещё не завершённый) месяц, масштабированный на полный
        // месяц по прошедшим дням — без этого прогноз не появлялся бы весь первый месяц
        // использования приложения, хотя данные для грубой оценки уже есть.
        val (monthlyExpenseByCategory, monthlyIncome) = if (hasCompletedMonthData) {
            averageExpenseByCategory(completedMonths, monthsToAverage) to
                averageTotal(completedMonths, monthsToAverage, TransactionType.INCOME)
        } else {
            val currentMonthStart = today.withDayOfMonth(1)
            val currentMonthTx = transactionRepository.observeByDateRange(
                currentMonthStart.toInstantAtStart(zoneId),
                today.plusDays(1).toInstantAtStart(zoneId)
            ).first()
            if (currentMonthTx.isEmpty()) return emptyList()

            val daysElapsed = today.dayOfMonth.coerceAtLeast(1)
            val daysInMonth = today.lengthOfMonth()
            val scale = BigDecimal(daysInMonth).divide(BigDecimal(daysElapsed), 4, RoundingMode.HALF_UP)

            val expenseByCategory = currentMonthTx
                .filter { it.transactionType == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { sum, tx -> sum + tx.amountBase }.multiply(scale) }
            val income = currentMonthTx.sumAmountBase(TransactionType.INCOME).multiply(scale)
            expenseByCategory to income
        }

        if (monthlyExpenseByCategory.isEmpty() && monthlyIncome.signum() == 0) return emptyList()

        val categories = categoryRepository.observeAll().first().associateBy { it.id }
        val categoryForecasts = monthlyExpenseByCategory
            .filter { it.value.signum() > 0 }
            .map { (categoryId, amount) ->
                CategoryForecast(
                    categoryId = categoryId,
                    categoryName = categoryId?.let { categories[it]?.name } ?: "Без категории",
                    predictedAmount = amount.coerceAtLeastZero()
                )
            }
            .sortedByDescending { it.predictedAmount }

        val currentMonth = YearMonth.from(today)
        return (1..monthsAhead).map { step ->
            MonthForecast(
                yearMonth = currentMonth.plusMonths(step.toLong()),
                categoryForecasts = categoryForecasts,
                predictedIncome = monthlyIncome.coerceAtLeastZero()
            )
        }
    }

    /** Среднемесячная трата по каждой категории — месяцы без неё считаются нулём (честное среднее). */
    private fun averageExpenseByCategory(
        months: List<List<ru.vogu.smartfinance.domain.model.Transaction>>,
        monthsCount: Int
    ): Map<Long?, BigDecimal> {
        val totalsByCategory = mutableMapOf<Long?, BigDecimal>()
        months.forEach { monthTx ->
            monthTx.filter { it.transactionType == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .forEach { (categoryId, txs) ->
                    val sum = txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amountBase }
                    totalsByCategory[categoryId] = (totalsByCategory[categoryId] ?: BigDecimal.ZERO) + sum
                }
        }
        return totalsByCategory.mapValues { it.value.divide(BigDecimal(monthsCount), 2, RoundingMode.HALF_UP) }
    }

    private fun averageTotal(
        months: List<List<ru.vogu.smartfinance.domain.model.Transaction>>,
        monthsCount: Int,
        type: TransactionType
    ): BigDecimal {
        val total = months.fold(BigDecimal.ZERO) { sum, monthTx -> sum + monthTx.sumAmountBase(type) }
        return total.divide(BigDecimal(monthsCount), 2, RoundingMode.HALF_UP)
    }

    private fun BigDecimal.coerceAtLeastZero(): BigDecimal =
        if (signum() < 0) BigDecimal.ZERO else setScale(2, RoundingMode.HALF_UP)

    private fun List<ru.vogu.smartfinance.domain.model.Transaction>.sumAmountBase(type: TransactionType): BigDecimal =
        filter { it.transactionType == type }.fold(BigDecimal.ZERO) { sum, tx -> sum + tx.amountBase }

    private fun LocalDate.toInstantAtStart(zoneId: ZoneId): Instant = atStartOfDay(zoneId).toInstant()
}
