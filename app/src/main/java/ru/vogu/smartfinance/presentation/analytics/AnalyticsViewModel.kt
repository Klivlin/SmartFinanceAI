package ru.vogu.smartfinance.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.domain.model.AnalyticsPeriod
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.usecase.GetAnalyticsSummaryUseCase
import javax.inject.Inject

/**
 * ViewModel экрана аналитики — CLAUDE.md §5.2/§6. Период (неделя/месяц/год) — единственное
 * локальное UI-состояние; сами агрегаты пересчитываются реактивно через [flatMapLatest]
 * при каждой смене периода, источник правды — Room (как и во всём приложении).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val getAnalyticsSummaryUseCase: GetAnalyticsSummaryUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)

    val state: StateFlow<AnalyticsState> = selectedPeriod
        .flatMapLatest { period ->
            combine(
                getAnalyticsSummaryUseCase(period),
                categoryRepository.observeAll(),
                accountRepository.observeAll()
            ) { summary, categories, accounts ->
                AnalyticsState(
                    period = period,
                    summary = summary,
                    categoriesById = categories.associateBy { it.id },
                    accountsById = accounts.associateBy { it.id },
                    baseCurrency = Constants.DEFAULT_BASE_CURRENCY,
                    isLoading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun onIntent(intent: AnalyticsIntent) {
        when (intent) {
            is AnalyticsIntent.PeriodSelected -> selectedPeriod.value = intent.period
        }
    }
}
