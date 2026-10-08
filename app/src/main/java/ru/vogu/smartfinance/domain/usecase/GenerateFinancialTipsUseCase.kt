package ru.vogu.smartfinance.domain.usecase

import kotlinx.coroutines.flow.first
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val MAX_TIPS = 5
private const val BUDGET_WARNING_RATIO = 0.8
private const val TREND_ALERT_PERCENT = 15.0
private const val TOP_CATEGORY_SHARE_PERCENT = 40.0

/**
 * Простые финансовые советы, полностью на локальных данных — CLAUDE.md §2.5/§5.2. Никакого
 * обращения к Gemini: правила арифметические (лимиты бюджета, тренд месяц-к-месяцу, доля самой
 * крупной категории), а не анализ свободного текста — токены здесь тратить не на что.
 */
class GenerateFinancialTipsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val getSpendingForecastUseCase: GetSpendingForecastUseCase
) {
    suspend operator fun invoke(
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        baseCurrency: String
    ): List<String> {
        val today = now.atZone(zoneId).toLocalDate()
        val monthStart = today.withDayOfMonth(1)
        val nextMonthStart = monthStart.plusMonths(1)
        val prevMonthStart = monthStart.minusMonths(1)

        val categories = categoryRepository.observeAll().first()
        val thisMonthTx = transactionRepository.observeByDateRange(
            monthStart.toInstantAtStart(zoneId),
            nextMonthStart.toInstantAtStart(zoneId)
        ).first()
        val prevMonthTx = transactionRepository.observeByDateRange(
            prevMonthStart.toInstantAtStart(zoneId),
            monthStart.toInstantAtStart(zoneId)
        ).first()

        val thisExpense = thisMonthTx.sumAmountBase(TransactionType.EXPENSE)
        val prevExpense = prevMonthTx.sumAmountBase(TransactionType.EXPENSE)
        val thisIncome = thisMonthTx.sumAmountBase(TransactionType.INCOME)

        val tips = mutableListOf<String>()

        // 1) Лимиты бюджета по категориям (CLAUDE.md §2.5): превышен либо близок к превышению.
        categories.filter { it.monthlyBudget != null && it.monthlyBudget!!.signum() > 0 }
            .forEach { category ->
                val budget = category.monthlyBudget!!
                val spent = thisMonthTx
                    .filter { it.categoryId == category.id && it.transactionType == TransactionType.EXPENSE }
                    .fold(BigDecimal.ZERO) { sum, tx -> sum + tx.amountBase }
                val ratio = spent.divide(budget, 4, RoundingMode.HALF_UP).toDouble()
                when {
                    ratio >= 1.0 -> tips += "Лимит по категории «${category.name}» превышен: " +
                        "${spent.formatAsCurrency(baseCurrency)} из ${budget.formatAsCurrency(baseCurrency)}."
                    ratio >= BUDGET_WARNING_RATIO -> tips += "Вы уже использовали ${(ratio * 100).toInt()}% " +
                        "лимита по категории «${category.name}»."
                }
            }

        // 2) Тренд относительно прошлого месяца.
        if (prevExpense.signum() > 0) {
            val diffPercent = thisExpense.subtract(prevExpense)
                .divide(prevExpense, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toDouble()
            if (diffPercent >= TREND_ALERT_PERCENT) {
                tips += "Расходы выросли на ${diffPercent.toInt()}% по сравнению с прошлым месяцем."
            } else if (diffPercent <= -TREND_ALERT_PERCENT) {
                tips += "Расходы снизились на ${(-diffPercent).toInt()}% по сравнению с прошлым месяцем — так держать."
            }
        }

        // 3) Потенциал накоплений.
        if (thisIncome.signum() > 0) {
            val surplus = thisIncome.subtract(thisExpense)
            if (surplus.signum() > 0) {
                tips += "В этом месяце доходы превышают расходы на ${surplus.formatAsCurrency(baseCurrency)} — " +
                    "можно отложить эту сумму на накопительный счёт."
            }
        }

        // 4) Самая крупная статья расходов текущего месяца — также нужна ниже для совета (5).
        val byCategory = thisMonthTx
            .filter { it.transactionType == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { sum, tx -> sum + tx.amountBase } }
        val topCategory = byCategory.maxByOrNull { it.value }
        if (thisExpense.signum() > 0 && topCategory != null) {
            val sharePercent = topCategory.value.divide(thisExpense, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)).toDouble()
            if (sharePercent >= TOP_CATEGORY_SHARE_PERCENT) {
                val name = categories.firstOrNull { it.id == topCategory.key }?.name ?: "Без категории"
                tips += "«$name» — самая крупная статья расходов: ${sharePercent.toInt()}% от всех трат за месяц."
            }
        }

        // 5) Прогноз на следующий месяц (тот же расчёт, что и в блоке "Прогноз" на экране
        // аналитики — CLAUDE.md: "выводы для советов делаются на основе анализа трат за месяц
        // и предположения, что будет в следующем"). НЕ про накопительный счёт — обычная
        // экстраполяция обычных расходов/доходов, см. doc [GetSpendingForecastUseCase].
        getSpendingForecastUseCase(now = now, zoneId = zoneId).firstOrNull()?.let { next ->
            val savings = next.predictedSavings
            val topForecastCategory = next.categoryForecasts.firstOrNull()
            when {
                savings.signum() < 0 -> {
                    val overspend = savings.abs()
                    val suffix = topForecastCategory?.let {
                        " Больше всего по прогнозу уйдёт на «${it.categoryName}» — " +
                            "${it.predictedAmount.formatAsCurrency(baseCurrency)}, с неё и стоит начать сокращение."
                    } ?: ""
                    tips += "По прогнозу в следующем месяце расходы превысят доходы примерно на " +
                        "${overspend.formatAsCurrency(baseCurrency)}, если траты не изменятся.$suffix"
                }
                savings.signum() > 0 -> {
                    val suffix = topForecastCategory?.let {
                        " Больше всего уйдёт на «${it.categoryName}» (~${it.predictedAmount.formatAsCurrency(baseCurrency)})."
                    } ?: ""
                    tips += "По текущему темпу в следующем месяце получится отложить " +
                        "примерно ${savings.formatAsCurrency(baseCurrency)}.$suffix"
                }
            }
        }

        return tips.take(MAX_TIPS)
    }

    private fun List<ru.vogu.smartfinance.domain.model.Transaction>.sumAmountBase(type: TransactionType): BigDecimal =
        filter { it.transactionType == type }.fold(BigDecimal.ZERO) { sum, tx -> sum + tx.amountBase }

    private fun LocalDate.toInstantAtStart(zoneId: ZoneId): Instant = atStartOfDay(zoneId).toInstant()
}
