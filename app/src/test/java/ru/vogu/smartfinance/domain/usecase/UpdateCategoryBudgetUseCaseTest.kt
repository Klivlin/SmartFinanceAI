package ru.vogu.smartfinance.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import java.math.BigDecimal

class UpdateCategoryBudgetUseCaseTest {

    private val categoryRepository: CategoryRepository = mockk()
    private val useCase = UpdateCategoryBudgetUseCase(categoryRepository)

    @Test
    fun `zero or negative budget is rejected without touching the repository`() = runTest {
        val result = useCase(1L, BigDecimal.ZERO)

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { categoryRepository.updateCategoryBudget(any(), any()) }
    }

    @Test
    fun `positive budget is saved via the repository`() = runTest {
        coEvery { categoryRepository.updateCategoryBudget(1L, BigDecimal("1500")) } returns Unit

        val result = useCase(1L, BigDecimal("1500"))

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) { categoryRepository.updateCategoryBudget(1L, BigDecimal("1500")) }
    }

    @Test
    fun `null budget clears the limit via the repository`() = runTest {
        coEvery { categoryRepository.updateCategoryBudget(1L, null) } returns Unit

        val result = useCase(1L, null)

        assertTrue(result is Resource.Success)
        coVerify(exactly = 1) { categoryRepository.updateCategoryBudget(1L, null) }
    }

    @Test
    fun `repository failure is surfaced as an error`() = runTest {
        coEvery { categoryRepository.updateCategoryBudget(any(), any()) } throws IllegalStateException("db error")

        val result = useCase(1L, BigDecimal("100"))

        assertTrue(result is Resource.Error)
    }
}
