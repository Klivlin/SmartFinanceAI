package ru.vogu.smartfinance.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.TransactionRepository

class UndoTransactionUseCaseTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val useCase = UndoTransactionUseCase(transactionRepository)

    @Test
    fun `successful delete returns success and deletes the transaction`() = runTest {
        coEvery { transactionRepository.deleteTransaction(42L) } returns Unit

        val result = useCase(42L)

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) { transactionRepository.deleteTransaction(42L) }
    }

    @Test
    fun `repository failure is wrapped as Resource Error`() = runTest {
        coEvery { transactionRepository.deleteTransaction(any()) } throws IllegalStateException("Транзакция id=1 не найдена.")

        val result = useCase(1L)

        assertTrue(result is Resource.Error)
        assertEquals("Транзакция id=1 не найдена.", (result as Resource.Error).message)
    }
}
