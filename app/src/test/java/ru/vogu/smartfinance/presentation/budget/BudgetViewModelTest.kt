package ru.vogu.smartfinance.presentation.budget

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.CategoryBudgetStatus
import ru.vogu.smartfinance.domain.usecase.CreateCategoryUseCase
import ru.vogu.smartfinance.domain.usecase.GetBudgetStatusUseCase
import ru.vogu.smartfinance.domain.usecase.GetIncomeCategoryStatusUseCase
import ru.vogu.smartfinance.domain.usecase.ReorderCategoryUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateCategoryAppearanceUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateCategoryBudgetUseCase
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val getBudgetStatusUseCase: GetBudgetStatusUseCase = mockk()
    private val getIncomeCategoryStatusUseCase: GetIncomeCategoryStatusUseCase = mockk()
    private val updateCategoryBudgetUseCase: UpdateCategoryBudgetUseCase = mockk()
    private val updateCategoryAppearanceUseCase: UpdateCategoryAppearanceUseCase = mockk()
    private val createCategoryUseCase: CreateCategoryUseCase = mockk()
    private val reorderCategoryUseCase: ReorderCategoryUseCase = mockk()

    private val statuses = MutableStateFlow<List<CategoryBudgetStatus>>(emptyList())

    private val foodCategory = Category(
        id = 1L, name = "Еда", iconName = "restaurant", colorHex = "#FF7043",
        monthlyBudget = BigDecimal("1000"), isIncomeCategory = false, isSystem = true, sortOrder = 1
    )

    private lateinit var viewModel: BudgetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { getBudgetStatusUseCase() } returns statuses
        every { getIncomeCategoryStatusUseCase() } returns MutableStateFlow(emptyList())
        viewModel = BudgetViewModel(
            getBudgetStatusUseCase,
            getIncomeCategoryStatusUseCase,
            updateCategoryBudgetUseCase,
            updateCategoryAppearanceUseCase,
            createCategoryUseCase,
            reorderCategoryUseCase
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
    fun `editing a known category opens the dialog with that category`() = runTest {
        keepStateAlive()
        statuses.value = listOf(
            CategoryBudgetStatus(category = foodCategory, spentThisMonth = BigDecimal("500"), usagePercent = 50, isWarning = false, isExceeded = false)
        )

        viewModel.onIntent(BudgetIntent.EditBudgetRequested(1L))

        assertEquals(foodCategory, viewModel.state.value.editingCategory)
    }

    @Test
    fun `saving an invalid amount surfaces an error without calling the use case`() = runTest {
        keepStateAlive()
        statuses.value = listOf(
            CategoryBudgetStatus(category = foodCategory, spentThisMonth = BigDecimal.ZERO, usagePercent = null, isWarning = false, isExceeded = false)
        )
        viewModel.onIntent(BudgetIntent.EditBudgetRequested(1L))

        viewModel.onIntent(BudgetIntent.SaveBudget("не число"))

        assertEquals("Введите корректную сумму лимита.", viewModel.state.value.errorMessage)
        coVerify(exactly = 0) { updateCategoryBudgetUseCase(any(), any()) }
    }

    @Test
    fun `saving a valid amount calls the use case and closes the dialog`() = runTest {
        keepStateAlive()
        statuses.value = listOf(
            CategoryBudgetStatus(category = foodCategory, spentThisMonth = BigDecimal.ZERO, usagePercent = null, isWarning = false, isExceeded = false)
        )
        viewModel.onIntent(BudgetIntent.EditBudgetRequested(1L))
        coEvery { updateCategoryBudgetUseCase(1L, BigDecimal("1500")) } returns Resource.Success(Unit)

        viewModel.onIntent(BudgetIntent.SaveBudget("1500"))

        coVerify(exactly = 1) { updateCategoryBudgetUseCase(1L, BigDecimal("1500")) }
        assertNull(viewModel.state.value.editingCategory)
    }

    @Test
    fun `removing a budget clears it via the use case`() = runTest {
        keepStateAlive()
        coEvery { updateCategoryBudgetUseCase(1L, null) } returns Resource.Success(Unit)

        viewModel.onIntent(BudgetIntent.RemoveBudget(1L))

        coVerify(exactly = 1) { updateCategoryBudgetUseCase(1L, null) }
    }
}
