package ru.vogu.smartfinance.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.NetworkMonitor
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.dao.AccountDao
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.core.network.GeminiClient
import ru.vogu.smartfinance.core.security.PiiFilter
import ru.vogu.smartfinance.data.parser.MultiExpenseSplitter
import ru.vogu.smartfinance.data.parser.RegexLocalParser
import ru.vogu.smartfinance.domain.model.ParsedExpense
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import java.math.BigDecimal

class NliExpenseRepositoryImplTest {

    private val categoryDao: CategoryDao = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val geminiClient: GeminiClient = mockk()
    private val networkMonitor: NetworkMonitor = mockk()
    private val semanticAliasRepository: SemanticAliasRepository = mockk()

    private lateinit var repository: NliExpenseRepositoryImpl

    @Before
    fun setUp() {
        repository = NliExpenseRepositoryImpl(
            piiFilter = PiiFilter(),
            regexLocalParser = RegexLocalParser(),
            multiExpenseSplitter = MultiExpenseSplitter(RegexLocalParser()),
            semanticAliasRepository = semanticAliasRepository,
            categoryDao = categoryDao,
            accountDao = accountDao,
            geminiClient = geminiClient,
            networkMonitor = networkMonitor
        )
        coEvery { semanticAliasRepository.resolveCategoryId(any()) } returns null
    }

    @Test
    fun `tier1 success does not call gemini or require network`() = runTest {
        val result = repository.parseUserInput("такси 450")

        assertTrue(result is Resource.Success)
        val expenses = (result as Resource.Success).data
        assertEquals(1, expenses.size)
        assertEquals(BigDecimal("450"), expenses[0].amount)
        assertEquals(TransactionSource.NLI, expenses[0].source)
        coVerify(exactly = 0) { geminiClient.parseExpenses(any(), any(), any(), any()) }
    }

    @Test
    fun `tier1 failure and offline returns error without calling gemini`() = runTest {
        coEvery { networkMonitor.isOnline() } returns false

        val result = repository.parseUserInput("вчера был сложный день, потратил много денег")

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { geminiClient.parseExpenses(any(), any(), any(), any()) }
    }

    @Test
    fun `tier1 failure and online falls back to gemini`() = runTest {
        coEvery { networkMonitor.isOnline() } returns true
        coEvery {
            geminiClient.parseExpenses(any(), any(), any(), any())
        } returns Resource.Success(
            listOf(
                ParsedExpense(
                    amount = BigDecimal("3500"),
                    title = "Спортзал",
                    transactionType = TransactionType.EXPENSE,
                    source = TransactionSource.NLI,
                    categoryId = 6L
                )
            )
        )

        // Намеренно НЕ "вчера 3500 за спортзал" — после того как RegexLocalParser научился
        // распознавать "вчера" (CLAUDE.md список доработок, п.1), эта фраза уже разбирается
        // Tier 1 локально (0 токенов) и до Gemini не доходит — см. отдельный тест ниже. Здесь
        // нужна фраза, которую Tier 1 не разбирает и после вычитания даты (слова по обе стороны
        // от суммы не укладываются ни в "[Сумма] [Товар]", ни в "[Товар] [Сумма]").
        val result = repository.parseUserInput("потратил вчера 3500 в спортзале")

        assertTrue(result is Resource.Success)
        assertEquals(1, (result as Resource.Success).data.size)
        coVerify(exactly = 1) { geminiClient.parseExpenses(any(), any(), any(), any()) }
    }

    @Test
    fun `yesterday keyword is resolved locally by tier1 without calling gemini`() = runTest {
        val result = repository.parseUserInput("вчера 3500 за спортзал")

        assertTrue(result is Resource.Success)
        val expense = (result as Resource.Success).data.single()
        assertEquals(java.time.LocalDate.now().minusDays(1), expense.date)
        coVerify(exactly = 0) { geminiClient.parseExpenses(any(), any(), any(), any()) }
    }

    @Test
    fun `blank input is rejected without touching any tier`() = runTest {
        val result = repository.parseUserInput("   ")
        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { geminiClient.parseExpenses(any(), any(), any(), any()) }
    }

    @Test
    fun `unknown merchant with no local match is classified via gemini and learned`() = runTest {
        // "яндекс плюс" не входит во встроенный словарь — это ровно случай собственной
        // категории пользователя ("Подписки"), которую нельзя было захардкодить заранее.
        coEvery { networkMonitor.isOnline() } returns true
        coEvery { categoryDao.getAllOnce() } returns emptyList()
        coEvery { geminiClient.classifyCategory(any(), any()) } returns Resource.Success(42L)
        coEvery { semanticAliasRepository.upsert(any(), any(), any()) } returns Unit

        val result = repository.parseUserInput("яндекс плюс 300")

        assertTrue(result is Resource.Success)
        val expense = (result as Resource.Success).data.single()
        assertEquals(42L, expense.categoryId)
        coVerify(exactly = 1) { geminiClient.classifyCategory("Яндекс плюс", emptyList()) }
        coVerify(exactly = 1) { semanticAliasRepository.upsert("яндекс плюс", 42L) }
    }

    @Test
    fun `unknown merchant offline is left without a category instead of failing`() = runTest {
        coEvery { networkMonitor.isOnline() } returns false

        val result = repository.parseUserInput("яндекс плюс 300")

        assertTrue(result is Resource.Success)
        val expense = (result as Resource.Success).data.single()
        assertEquals(null, expense.categoryId)
        coVerify(exactly = 0) { geminiClient.classifyCategory(any(), any()) }
    }
}
