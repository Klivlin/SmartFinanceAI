package ru.vogu.smartfinance.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.AccountHint
import ru.vogu.smartfinance.domain.model.ParsedExpense
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.NliExpenseRepository
import ru.vogu.smartfinance.domain.repository.SemanticAliasRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

class ProcessNliInputUseCaseTest {

    private val nliExpenseRepository: NliExpenseRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val accountRepository: AccountRepository = mockk()
    private val semanticAliasRepository: SemanticAliasRepository = mockk(relaxed = true)

    private lateinit var useCase: ProcessNliInputUseCase

    private val fakeAccount = Account(
        id = 1L,
        name = "Основной",
        type = AccountType.DEBIT_CARD,
        balance = BigDecimal.ZERO,
        currency = "RUB",
        isCredit = false,
        creditLimit = null,
        gracePeriodDays = null,
        statementDay = null,
        isArchived = false,
        sortOrder = 0,
        createdAt = Instant.now()
    )

    @Before
    fun setUp() {
        useCase = ProcessNliInputUseCase(
            nliExpenseRepository = nliExpenseRepository,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            semanticAliasRepository = semanticAliasRepository
        )
        coEvery { accountRepository.findBestMatch(any()) } returns fakeAccount
    }

    @Test
    fun `successful tier1 parse creates one transaction without learning alias`() = runTest {
        val parsed = ParsedExpense(
            amount = BigDecimal("450"),
            title = "Такси",
            transactionType = TransactionType.EXPENSE,
            source = TransactionSource.NLI,
            categoryId = null // Tier1 никогда не резолвит категорию
        )
        coEvery { nliExpenseRepository.parseUserInput("такси 450") } returns Resource.Success(listOf(parsed))
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = any(), rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        } answers { fakeTransaction(amount = BigDecimal("450"), categoryId = 9L) }

        val result = useCase("такси 450")

        assertTrue(result is Resource.Success)
        assertEquals(1, (result as Resource.Success).data.size)
        // categoryId был null -> использован дефолт "Другое" (id=9), а не выученный алиас
        coVerify(exactly = 0) { semanticAliasRepository.upsert(any(), any(), any()) }
    }

    @Test
    fun `llm-resolved category triggers alias learning`() = runTest {
        val parsed = ParsedExpense(
            amount = BigDecimal("3500"),
            title = "Спортзал",
            transactionType = TransactionType.EXPENSE,
            source = TransactionSource.NLI,
            categoryId = 6L
        )
        coEvery { nliExpenseRepository.parseUserInput(any()) } returns Resource.Success(listOf(parsed))
        val categorySlot = slot<Long?>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = capture(categorySlot),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = any(), rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        } answers { fakeTransaction(amount = BigDecimal("3500"), categoryId = 6L) }

        val result = useCase("вчера 3500 за спортзал")

        assertTrue(result is Resource.Success)
        assertEquals(6L, categorySlot.captured)
        coVerify(exactly = 1) { semanticAliasRepository.upsert("спортзал", 6L, fakeAccount.id) }
    }

    @Test
    fun `transfer via nli is downgraded to expense (no reliable destination account)`() = runTest {
        val parsed = ParsedExpense(
            amount = BigDecimal("1000"),
            title = "Перевод",
            transactionType = TransactionType.TRANSFER,
            source = TransactionSource.NLI,
            accountHint = AccountHint("сбер", "sber")
        )
        coEvery { nliExpenseRepository.parseUserInput(any()) } returns Resource.Success(listOf(parsed))
        val typeSlot = slot<TransactionType>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = any(), transactionType = capture(typeSlot), title = any(), notes = any(),
                timestamp = any(), source = any(), rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        } answers { fakeTransaction(amount = BigDecimal("1000"), categoryId = 7L, type = TransactionType.EXPENSE) }

        useCase("перевёл 1000 на сбер")

        assertEquals(TransactionType.EXPENSE, typeSlot.captured)
    }

    @Test
    fun `error from nli repository propagates unchanged`() = runTest {
        coEvery { nliExpenseRepository.parseUserInput(any()) } returns Resource.Error("Нет сети")

        val result = useCase("что-то непонятное")

        assertTrue(result is Resource.Error)
        assertEquals("Нет сети", (result as Resource.Error).message)
    }

    private fun fakeTransaction(
        amount: BigDecimal,
        categoryId: Long?,
        type: TransactionType = TransactionType.EXPENSE
    ) = Transaction(
        id = 1L,
        accountId = fakeAccount.id,
        toAccountId = null,
        categoryId = categoryId,
        amount = amount,
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = amount,
        transactionType = type,
        title = "Тест",
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
}
