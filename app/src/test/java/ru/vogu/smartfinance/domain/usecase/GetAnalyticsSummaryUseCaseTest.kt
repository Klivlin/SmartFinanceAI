package ru.vogu.smartfinance.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.AnalyticsPeriod
import ru.vogu.smartfinance.domain.model.CategoryAmount
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.DailySpendingPoint
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GetAnalyticsSummaryUseCaseTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()

    private val useCase = GetAnalyticsSummaryUseCase(transactionRepository, categoryRepository)

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-28T12:00:00Z")

    private val foodCategory = Category(
        id = 1L, name = "Еда", iconName = "restaurant", colorHex = "#FF7043",
        monthlyBudget = null, isIncomeCategory = false, isSystem = true, sortOrder = 1
    )
    private val incomeCategory = Category(
        id = 8L, name = "Доход", iconName = "payments", colorHex = "#26A69A",
        monthlyBudget = null, isIncomeCategory = true, isSystem = true, sortOrder = 8
    )

    private fun transaction(
        id: Long,
        amountBase: BigDecimal,
        type: TransactionType,
        categoryId: Long?,
        timestamp: Instant
    ) = Transaction(
        id = id,
        accountId = 1L,
        toAccountId = null,
        categoryId = categoryId,
        amount = amountBase,
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = amountBase,
        transactionType = type,
        title = "Операция $id",
        notes = null,
        timestamp = timestamp,
        source = TransactionSource.NLI,
        rawInputText = null,
        transferFeeAmount = null,
        recurringRuleId = null,
        isSplit = false,
        createdAt = timestamp,
        updatedAt = timestamp
    )

    @Test
    fun `month summary sums totals, breaks down by category and gap-fills the daily chart`() = runTest {
        val transactions = listOf(
            transaction(1L, BigDecimal("1000"), TransactionType.EXPENSE, categoryId = 1L, timestamp = Instant.parse("2026-09-05T10:00:00Z")),
            transaction(2L, BigDecimal("500"), TransactionType.EXPENSE, categoryId = 1L, timestamp = Instant.parse("2026-09-10T10:00:00Z")),
            transaction(3L, BigDecimal("300"), TransactionType.EXPENSE, categoryId = null, timestamp = Instant.parse("2026-09-10T11:00:00Z")),
            transaction(4L, BigDecimal("5000"), TransactionType.INCOME, categoryId = 8L, timestamp = Instant.parse("2026-09-15T10:00:00Z"))
        )
        every { transactionRepository.observeByDateRange(any(), any()) } returns flowOf(transactions)
        every { transactionRepository.observeSpendingByCategory(any(), any()) } returns flowOf(
            listOf(
                CategoryAmount(categoryId = 1L, totalAmountBase = BigDecimal("1500")),
                CategoryAmount(categoryId = null, totalAmountBase = BigDecimal("300"))
            )
        )
        every { transactionRepository.observeDailySpending(any(), any()) } returns flowOf(
            listOf(
                DailySpendingPoint(date = LocalDate.of(2026, 9, 5), amountBase = BigDecimal("1000")),
                DailySpendingPoint(date = LocalDate.of(2026, 9, 10), amountBase = BigDecimal("800"))
            )
        )
        every { categoryRepository.observeAll() } returns flowOf(listOf(foodCategory, incomeCategory))

        val summary = useCase(AnalyticsPeriod.MONTH, now, utc).first()

        assertEquals(LocalDate.of(2026, 9, 1), summary.periodStart)
        assertEquals(LocalDate.of(2026, 9, 30), summary.periodEndInclusive)
        assertEquals(BigDecimal("1800"), summary.totalExpenses)
        assertEquals(BigDecimal("5000"), summary.totalIncome)

        assertEquals(2, summary.categoryBreakdown.size)
        assertEquals(foodCategory, summary.categoryBreakdown[0].category)
        assertEquals(BigDecimal("1500"), summary.categoryBreakdown[0].amountBase)
        assertTrue(summary.categoryBreakdown[0].percentage > summary.categoryBreakdown[1].percentage)
        assertNull(summary.categoryBreakdown[1].category)

        // Сентябрь — 30 дней, DAO вернул только 2 непустых, остальные должны быть дозаполнены нулями.
        assertEquals(30, summary.dailySpending.size)
        assertEquals(LocalDate.of(2026, 9, 1), summary.dailySpending.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), summary.dailySpending.last().date)
        assertEquals(BigDecimal("1000"), summary.dailySpending.first { it.date == LocalDate.of(2026, 9, 5) }.amountBase)
        assertEquals(BigDecimal.ZERO, summary.dailySpending.first { it.date == LocalDate.of(2026, 9, 6) }.amountBase)

        assertEquals(listOf(1L, 2L, 3L), summary.topTransactions.map { it.id })
    }

    @Test
    fun `week period spans the six days before today plus today`() = runTest {
        every { transactionRepository.observeByDateRange(any(), any()) } returns flowOf(emptyList())
        every { transactionRepository.observeSpendingByCategory(any(), any()) } returns flowOf(emptyList())
        every { transactionRepository.observeDailySpending(any(), any()) } returns flowOf(emptyList())
        every { categoryRepository.observeAll() } returns flowOf(emptyList())

        val summary = useCase(AnalyticsPeriod.WEEK, now, utc).first()

        assertEquals(LocalDate.of(2026, 9, 22), summary.periodStart)
        assertEquals(LocalDate.of(2026, 9, 28), summary.periodEndInclusive)
        assertEquals(7, summary.dailySpending.size)
        assertTrue(summary.categoryBreakdown.isEmpty())
        assertTrue(summary.topTransactions.isEmpty())
    }
}
