package ru.vogu.smartfinance.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

class ManageAccountUseCaseTest {

    private val accountRepository: AccountRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()

    private val useCase = ManageAccountUseCase(accountRepository, transactionRepository)

    private fun fakeTransaction() = Transaction(
        id = 1L, accountId = 1L, toAccountId = 2L, categoryId = null,
        amount = BigDecimal("500"), originalCurrency = "RUB", exchangeRate = BigDecimal.ONE,
        amountBase = BigDecimal("500"), transactionType = TransactionType.TRANSFER,
        title = "Перевод между счетами", notes = null, timestamp = Instant.now(),
        source = TransactionSource.MANUAL, rawInputText = null, transferFeeAmount = null,
        recurringRuleId = null, isSplit = false, createdAt = Instant.now(), updatedAt = Instant.now()
    )

    @Test
    fun `blank name is rejected without touching the repository`() = runTest {
        val result = useCase.createAccount(name = "  ", type = AccountType.CASH, currency = "RUB", initialBalance = BigDecimal.ZERO)

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { accountRepository.createAccount(any()) }
    }

    @Test
    fun `credit card without a positive credit limit is rejected`() = runTest {
        val result = useCase.createAccount(
            name = "Тинькофф Платинум",
            type = AccountType.CREDIT_CARD,
            currency = "RUB",
            initialBalance = BigDecimal.ZERO,
            creditLimit = null
        )

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { accountRepository.createAccount(any()) }
    }

    @Test
    fun `valid account is created with the normalized currency code`() = runTest {
        val accountSlot = slot<Account>()
        coEvery { accountRepository.createAccount(capture(accountSlot)) } returns 42L

        val result = useCase.createAccount(
            name = "Сберкопилка",
            type = AccountType.SAVINGS,
            currency = "rub",
            initialBalance = BigDecimal("1000")
        )

        assertTrue(result is Resource.Success)
        assertEquals(42L, (result as Resource.Success).data.id)
        assertEquals("RUB", accountSlot.captured.currency)
        assertEquals(BigDecimal("1000"), accountSlot.captured.balance)
    }

    @Test
    fun `archive delegates to the repository`() = runTest {
        coEvery { accountRepository.archiveAccount(5L) } returns Unit

        val result = useCase.archiveAccount(5L)

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) { accountRepository.archiveAccount(5L) }
    }

    @Test
    fun `transfer to the same account is rejected without calling the repository`() = runTest {
        val result = useCase.transfer(fromAccountId = 1L, toAccountId = 1L, amount = BigDecimal("100"))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionRepository.createTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `negative fee is rejected`() = runTest {
        val result = useCase.transfer(fromAccountId = 1L, toAccountId = 2L, amount = BigDecimal("100"), feeAmount = BigDecimal("-1"))

        assertTrue(result is Resource.Error)
    }

    @Test
    fun `valid transfer delegates to the transaction repository as TRANSFER`() = runTest {
        coEvery {
            transactionRepository.createTransaction(
                accountId = 1L, toAccountId = 2L, categoryId = null,
                amount = BigDecimal("500"), transactionType = TransactionType.TRANSFER,
                title = any(), notes = any(), timestamp = any(), source = TransactionSource.MANUAL,
                rawInputText = any(), originalCurrency = any(), transferFeeAmount = BigDecimal("10"),
                recurringRuleId = any(), splitItems = any()
            )
        } returns fakeTransaction()

        val result = useCase.transfer(fromAccountId = 1L, toAccountId = 2L, amount = BigDecimal("500"), feeAmount = BigDecimal("10"))

        assertTrue(result is Resource.Success)
    }
}
