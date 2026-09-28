package ru.vogu.smartfinance.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

class ObserveDashboardSummaryUseCaseTest {

    private val accountRepository: AccountRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()

    private val useCase = ObserveDashboardSummaryUseCase(accountRepository, transactionRepository, categoryRepository)

    private fun account(id: Long, balance: BigDecimal, currency: String) = Account(
        id = id,
        name = "Счёт $id",
        type = AccountType.DEBIT_CARD,
        balance = balance,
        currency = currency,
        isCredit = false,
        creditLimit = null,
        gracePeriodDays = null,
        statementDay = null,
        isArchived = false,
        sortOrder = 0,
        createdAt = Instant.now()
    )

    private fun expense(id: Long, amountBase: BigDecimal) = Transaction(
        id = id,
        accountId = 1L,
        toAccountId = null,
        categoryId = 1L,
        amount = amountBase,
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = amountBase,
        transactionType = TransactionType.EXPENSE,
        title = "Трата $id",
        notes = null,
        timestamp = Instant.now(),
        source = TransactionSource.NLI,
        rawInputText = null,
        transferFeeAmount = null,
        recurringRuleId = null,
        isSplit = false,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun category(id: Long, budget: BigDecimal?) = Category(
        id = id,
        name = "Категория $id",
        iconName = "category",
        colorHex = "#8D6E63",
        monthlyBudget = budget,
        isIncomeCategory = false,
        isSystem = true,
        sortOrder = 0
    )

    @Test
    fun `total balance sums only base-currency accounts`() = runTest {
        every { accountRepository.observeActive() } returns flowOf(
            listOf(account(1L, BigDecimal("1000"), "RUB"), account(2L, BigDecimal("500"), "RUB"), account(3L, BigDecimal("300"), "USD"))
        )
        every { transactionRepository.observeByDateRange(any(), any()) } returns flowOf(emptyList())
        every { categoryRepository.observeBudgeted() } returns flowOf(emptyList())

        val summary = useCase().first()

        assertEquals(BigDecimal("1500"), summary.totalBalance)
        assertEquals("RUB", summary.baseCurrency)
    }

    @Test
    fun `day and month ranges are queried as two distinct calls and summed independently via amountBase`() = runTest {
        // UseCase вызывает observeByDateRange дважды с РАЗНЫМИ границами (день, затем месяц) —
        // аргументы функции вычисляются слева направо ДО вызова combine(), поэтому 1-й вызов
        // здесь гарантированно соответствует диапазону "сегодня", 2-й — диапазону "этот месяц".
        var callIndex = 0
        every { accountRepository.observeActive() } returns flowOf(emptyList())
        every { categoryRepository.observeBudgeted() } returns flowOf(emptyList())
        every { transactionRepository.observeByDateRange(any(), any()) } answers {
            callIndex += 1
            if (callIndex == 1) {
                flowOf(listOf(expense(1L, BigDecimal("100"))))
            } else {
                flowOf(listOf(expense(1L, BigDecimal("100")), expense(2L, BigDecimal("900"))))
            }
        }

        val summary = useCase().first()

        assertEquals(BigDecimal("100"), summary.spentToday)
        assertEquals(BigDecimal("1000"), summary.spentThisMonth)
    }

    @Test
    fun `income transactions are excluded from spending totals`() = runTest {
        val income = expense(1L, BigDecimal("5000")).copy(transactionType = TransactionType.INCOME)
        every { accountRepository.observeActive() } returns flowOf(emptyList())
        every { transactionRepository.observeByDateRange(any(), any()) } returns flowOf(listOf(income))
        every { categoryRepository.observeBudgeted() } returns flowOf(emptyList())

        val summary = useCase().first()

        assertEquals(BigDecimal.ZERO, summary.spentToday)
    }

    @Test
    fun `budget usage is null when no category has a monthly budget`() = runTest {
        every { accountRepository.observeActive() } returns flowOf(emptyList())
        every { transactionRepository.observeByDateRange(any(), any()) } returns flowOf(emptyList())
        every { categoryRepository.observeBudgeted() } returns flowOf(listOf(category(1L, null)))

        val summary = useCase().first()

        assertNull(summary.monthlyBudgetTotal)
        assertNull(summary.budgetUsagePercent)
    }

    @Test
    fun `budget usage percent and warning flags are computed from spend vs budget total`() = runTest {
        every { accountRepository.observeActive() } returns flowOf(emptyList())
        every {
            transactionRepository.observeByDateRange(any(), any())
        } returns flowOf(listOf(expense(1L, BigDecimal("900"))))
        every {
            categoryRepository.observeBudgeted()
        } returns flowOf(listOf(category(1L, BigDecimal("600")), category(2L, BigDecimal("400"))))

        val summary = useCase().first()

        assertEquals(BigDecimal("1000"), summary.monthlyBudgetTotal)
        assertEquals(90, summary.budgetUsagePercent)
        assertTrue(summary.isBudgetWarning)
        assertFalse(summary.isBudgetExceeded)
    }
}
