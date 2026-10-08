package ru.vogu.smartfinance.presentation.analytics

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.domain.model.AnalyticsPeriod
import ru.vogu.smartfinance.domain.model.AnalyticsSummary
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.usecase.GenerateFinancialTipsUseCase
import ru.vogu.smartfinance.domain.usecase.GetAnalyticsSummaryUseCase
import ru.vogu.smartfinance.domain.usecase.GetSpendingForecastUseCase
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private val getAnalyticsSummaryUseCase: GetAnalyticsSummaryUseCase = mockk()
    private val getSpendingForecastUseCase: GetSpendingForecastUseCase = mockk()
    private val generateFinancialTipsUseCase: GenerateFinancialTipsUseCase = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val accountRepository: AccountRepository = mockk()

    private lateinit var viewModel: AnalyticsViewModel

    private fun summaryFor(period: AnalyticsPeriod) = AnalyticsSummary(
        period = period,
        periodStart = LocalDate.of(2026, 9, 1),
        periodEndInclusive = LocalDate.of(2026, 9, 30),
        totalExpenses = BigDecimal("1000"),
        totalIncome = BigDecimal.ZERO,
        categoryBreakdown = emptyList(),
        dailySpending = emptyList(),
        topTransactions = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { categoryRepository.observeAll() } returns flowOf(emptyList())
        every { accountRepository.observeAll() } returns flowOf(emptyList())
        every { getAnalyticsSummaryUseCase(AnalyticsPeriod.MONTH) } returns flowOf(summaryFor(AnalyticsPeriod.MONTH))
        every { getAnalyticsSummaryUseCase(AnalyticsPeriod.WEEK) } returns flowOf(summaryFor(AnalyticsPeriod.WEEK))
        every { getAnalyticsSummaryUseCase(AnalyticsPeriod.YEAR) } returns flowOf(summaryFor(AnalyticsPeriod.YEAR))
        coEvery { getSpendingForecastUseCase() } returns emptyList()
        coEvery { generateFinancialTipsUseCase(baseCurrency = any()) } returns emptyList()

        viewModel = AnalyticsViewModel(
            getAnalyticsSummaryUseCase = getAnalyticsSummaryUseCase,
            getSpendingForecastUseCase = getSpendingForecastUseCase,
            generateFinancialTipsUseCase = generateFinancialTipsUseCase,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.keepStateAlive() {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    @Test
    fun `defaults to the current month`() = runTest {
        keepStateAlive()

        assertEquals(AnalyticsPeriod.MONTH, viewModel.state.value.period)
        assertEquals(AnalyticsPeriod.MONTH, viewModel.state.value.summary?.period)
    }

    @Test
    fun `selecting a period re-subscribes to the matching summary flow`() = runTest {
        keepStateAlive()

        viewModel.onIntent(AnalyticsIntent.PeriodSelected(AnalyticsPeriod.YEAR))

        assertEquals(AnalyticsPeriod.YEAR, viewModel.state.value.period)
        assertEquals(AnalyticsPeriod.YEAR, viewModel.state.value.summary?.period)
    }
}
