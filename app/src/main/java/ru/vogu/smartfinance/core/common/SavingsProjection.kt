package ru.vogu.smartfinance.core.common

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Одна точка прогноза накоплений: через сколько месяцев и какая сумма будет на счёте. */
data class SavingsProjectionPoint(val months: Int, val projectedBalance: BigDecimal)

/** Горизонты, которые показываются в калькуляторе прогноза — CLAUDE.md §2.1. */
val SAVINGS_PROJECTION_HORIZONS_MONTHS = listOf(1, 3, 6, 12, 24)

/**
 * Прогноз роста накопительного счёта — CLAUDE.md §2.1 ("сколько денег накапает через месяц,
 * 2, 3 и т.д."). Модель — ежемесячная капитализация процентов на ТЕКУЩИЙ баланс (без учёта
 * будущих пополнений/списаний, которые непредсказуемы): `FV = P * (1 + r/12/100)^n`.
 * Намеренно простая и прозрачная для пользователя модель, а не точный банковский расчёт day-count.
 */
fun projectSavingsGrowth(
    currentBalance: BigDecimal,
    annualRatePercent: BigDecimal,
    horizonsMonths: List<Int> = SAVINGS_PROJECTION_HORIZONS_MONTHS
): List<SavingsProjectionPoint> {
    if (currentBalance.signum() <= 0 || annualRatePercent.signum() <= 0) {
        return horizonsMonths.map { SavingsProjectionPoint(it, currentBalance) }
    }
    val monthlyRate = annualRatePercent
        .divide(BigDecimal(100), MathContext.DECIMAL64)
        .divide(BigDecimal(12), MathContext.DECIMAL64)
    val growthFactor = BigDecimal.ONE + monthlyRate

    return horizonsMonths.map { months ->
        val factor = growthFactor.pow(months, MathContext.DECIMAL64)
        val projected = currentBalance.multiply(factor, MathContext.DECIMAL64)
            .setScale(2, RoundingMode.HALF_UP)
        SavingsProjectionPoint(months, projected)
    }
}
