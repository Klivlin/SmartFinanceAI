package ru.vogu.smartfinance.presentation.analytics

import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.AnalyticsPeriod
import ru.vogu.smartfinance.domain.model.AnalyticsSummary
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.usecase.MonthForecast

/**
 * MVI-контракт экрана аналитики — CLAUDE.md §5.2/§6. Экран только читает агрегаты
 * из [ru.vogu.smartfinance.domain.usecase.GetAnalyticsSummaryUseCase] — без Effect-канала,
 * т.к. здесь нет однократных событий навигации/сайд-эффектов (в отличие от Chat/Scanner).
 */
sealed interface AnalyticsIntent {
    data class PeriodSelected(val period: AnalyticsPeriod) : AnalyticsIntent
}

data class AnalyticsState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val summary: AnalyticsSummary? = null,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val accountsById: Map<Long, Account> = emptyMap(),
    val baseCurrency: String = Constants.DEFAULT_BASE_CURRENCY,
    val isLoading: Boolean = true,
    /** Прогноз расходов/доходов на ближайшие месяцы (тренд по последним завершённым месяцам). */
    val forecast: List<MonthForecast> = emptyList(),
    /** Короткие финансовые советы на основе локальных данных — CLAUDE.md §5.2. */
    val tips: List<String> = emptyList()
)
