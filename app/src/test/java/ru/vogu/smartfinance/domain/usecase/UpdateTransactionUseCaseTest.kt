package ru.vogu.smartfinance.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

class UpdateTransactionUseCaseTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val useCase = UpdateTransactionUseCase(transactionRepository)

    private fun baseTransaction(
        amount: BigDecimal = BigDecimal("450"),
        title: String = "Такси",
        type: TransactionType = TransactionType.EXPENSE,
        toAccountId: Long? = null
    ) = Transaction(
        id = 1L,
        accountId = 1L,
        toAccountId = toAccountId,
        categoryId = 2L,
        amount = amount,
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = amount,
        transactionType = type,
        title = title,
        notes = null,
        timestamp = Instant.now(),
        source = TransactionSource.NLI,
        rawInputText = "такси 450",
        transferFeeAmount = null,
        recurringRuleId = null,
        isSplit = false,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `non-positive amount is rejected without touching the repository`() = runTest {
        val result = useCase(baseTransaction(amount = BigDecimal.ZERO))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionRepository.updateTransaction(any()) }
    }

    @Test
    fun `blank title is rejected`() = runTest {
        val result = useCase(baseTransaction(title = "   "))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionRepository.updateTransaction(any()) }
    }

    @Test
    fun `transfer without destination account is rejected`() = runTest {
        val result = useCase(baseTransaction(type = TransactionType.TRANSFER, toAccountId = null))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionRepository.updateTransaction(any()) }
    }

    @Test
    fun `transfer to the same account is rejected`() = runTest {
        val result = useCase(baseTransaction(type = TransactionType.TRANSFER, toAccountId = 1L))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { transactionRepository.updateTransaction(any()) }
    }

    @Test
    fun `valid transaction is persisted and returned`() = runTest {
        val transaction = baseTransaction()
        coEvery { transactionRepository.updateTransaction(transaction) } returns Unit

        val result = useCase(transaction)

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) { transactionRepository.updateTransaction(transaction) }
    }

    @Test
    fun `repository failure is wrapped as Resource Error`() = runTest {
        val transaction = baseTransaction()
        coEvery { transactionRepository.updateTransaction(transaction) } throws IllegalStateException("Счёт id=1 не найден.")

        val result = useCase(transaction)

        assertTrue(result is Resource.Error)
    }
}
