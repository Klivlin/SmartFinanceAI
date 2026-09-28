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
import ru.vogu.smartfinance.domain.model.CategoryAmount
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal

class GetBudgetStatusUseCaseTest {

    private val categoryRepository: CategoryRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()

    private val useCase = GetBudgetStatusUseCase(categoryRepository, transactionRepository)

    private val foodCategory = Category(
        id = 1L, name = "Еда", iconName = "restaurant", colorHex = "#FF7043",
        monthlyBudget = BigDecimal("1000"), isIncomeCategory = false, isSystem = true, sortOrder = 1
    )
    private val transportCategory = Category(
        id = 2L, name = "Транспорт", iconName = "directions_car", colorHex = "#42A5F5",
        monthlyBudget = null, isIncomeCategory = false, isSystem = true, sortOrder = 2
    )
    private val cafeCategory = Category(
        id = 3L, name = "Кафе", iconName = "local_cafe", colorHex = "#AB47BC",
        monthlyBudget = BigDecimal("100"), isIncomeCategory = false, isSystem = true, sortOrder = 3
    )

    @Test
    fun `categories with a budget are sorted first, usage thresholds computed per category`() = runTest {
        every { categoryRepository.observeExpenseCategories() } returns flowOf(
            listOf(transportCategory, foodCategory, cafeCategory)
        )
        every { transactionRepository.observeSpendingByCategory(any(), any()) } returns flowOf(
            listOf(
                CategoryAmount(categoryId = 1L, totalAmountBase = BigDecimal("850")),
                CategoryAmount(categoryId = 3L, totalAmountBase = BigDecimal("120"))
            )
        )

        val statuses = useCase().first()

        assertEquals(listOf(foodCategory.id, cafeCategory.id, transportCategory.id), statuses.map { it.category.id })

        val food = statuses.first { it.category.id == 1L }
        assertEquals(85, food.usagePercent)
        assertTrue(food.isWarning)
        assertFalse(food.isExceeded)

        val cafe = statuses.first { it.category.id == 3L }
        assertEquals(120, cafe.usagePercent)
        assertTrue(cafe.isWarning)
        assertTrue(cafe.isExceeded)

        val transport = statuses.first { it.category.id == 2L }
        assertEquals(BigDecimal.ZERO, transport.spentThisMonth)
        assertNull(transport.usagePercent)
        assertFalse(transport.isWarning)
        assertFalse(transport.isExceeded)
    }
}
