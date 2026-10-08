package ru.vogu.smartfinance.presentation.accounts

import app.cash.turbine.test
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.usecase.ManageAccountUseCase
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private val accountRepository: AccountRepository = mockk()
    private val manageAccountUseCase: ManageAccountUseCase = mockk()

    private val activeAccounts = MutableStateFlow<List<Account>>(emptyList())

    private val mainAccount = Account(
        id = 1L, name = "Основной", type = AccountType.DEBIT_CARD, balance = BigDecimal("1000"),
        currency = "RUB", isCredit = false, creditLimit = null, gracePeriodDays = null,
        statementDay = null, isArchived = false, sortOrder = 0, createdAt = Instant.now()
    )
    private val savingsAccount = mainAccount.copy(id = 2L, name = "Копилка", sortOrder = 1)

    private lateinit var viewModel: AccountsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { accountRepository.observeActive() } returns activeAccounts
        viewModel = AccountsViewModel(accountRepository, manageAccountUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.keepStateAlive() {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    @Test
    fun `creating an account with an invalid balance surfaces an error without calling the use case`() = runTest {
        keepStateAlive()

        viewModel.onIntent(
            AccountsIntent.CreateAccount(
                NewAccountForm(name = "Новый", type = AccountType.CASH, currency = "RUB", initialBalanceText = "не число")
            )
        )

        assertEquals("Введите корректный начальный баланс.", viewModel.state.value.errorMessage)
        coVerify(exactly = 0) { manageAccountUseCase.createAccount(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `creating a valid account closes the sheet and shows a toast`() = runTest {
        keepStateAlive()
        viewModel.onIntent(AccountsIntent.AddAccountRequested)
        coEvery {
            manageAccountUseCase.createAccount(
                name = "Копилка", type = AccountType.SAVINGS, currency = "RUB",
                initialBalance = BigDecimal("500"), creditLimit = null, gracePeriodDays = null, statementDay = null,
                interestRatePercent = null
            )
        } returns Resource.Success(savingsAccount)

        viewModel.effect.test {
            viewModel.onIntent(
                AccountsIntent.CreateAccount(
                    NewAccountForm(name = "Копилка", type = AccountType.SAVINGS, currency = "RUB", initialBalanceText = "500")
                )
            )

            val effect = awaitItem()
            assertTrue(effect is AccountsEffect.ShowToast)
        }
        assertFalse(viewModel.state.value.isAddAccountVisible)
    }

    @Test
    fun `transfer with a blank destination amount surfaces an error`() = runTest {
        keepStateAlive()
        activeAccounts.value = listOf(mainAccount, savingsAccount)
        viewModel.onIntent(AccountsIntent.TransferRequested(mainAccount.id))

        viewModel.onIntent(AccountsIntent.SubmitTransfer(toAccountId = savingsAccount.id, amountText = "", feeText = ""))

        assertEquals("Введите корректную сумму перевода.", viewModel.state.value.errorMessage)
        coVerify(exactly = 0) { manageAccountUseCase.transfer(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `valid transfer delegates to the use case and clears the sheet`() = runTest {
        keepStateAlive()
        activeAccounts.value = listOf(mainAccount, savingsAccount)
        viewModel.onIntent(AccountsIntent.TransferRequested(mainAccount.id))
        val fakeTransaction = Transaction(
            id = 10L, accountId = mainAccount.id, toAccountId = savingsAccount.id, categoryId = null,
            amount = BigDecimal("200"), originalCurrency = "RUB", exchangeRate = BigDecimal.ONE,
            amountBase = BigDecimal("200"), transactionType = TransactionType.TRANSFER,
            title = "Перевод между счетами", notes = null, timestamp = Instant.now(),
            source = TransactionSource.MANUAL, rawInputText = null, transferFeeAmount = null,
            recurringRuleId = null, isSplit = false, createdAt = Instant.now(), updatedAt = Instant.now()
        )
        coEvery {
            manageAccountUseCase.transfer(
                fromAccountId = mainAccount.id, toAccountId = savingsAccount.id, amount = BigDecimal("200"), feeAmount = null
            )
        } returns Resource.Success(fakeTransaction)

        viewModel.onIntent(AccountsIntent.SubmitTransfer(toAccountId = savingsAccount.id, amountText = "200", feeText = ""))

        coVerify(exactly = 1) {
            manageAccountUseCase.transfer(
                fromAccountId = mainAccount.id, toAccountId = savingsAccount.id, amount = BigDecimal("200"), feeAmount = null
            )
        }
        assertEquals(null, viewModel.state.value.transferFromAccountId)
    }

    @Test
    fun `archiving surfaces the use case error`() = runTest {
        keepStateAlive()
        coEvery { manageAccountUseCase.archiveAccount(1L) } returns Resource.Error("Не удалось архивировать счёт.")

        viewModel.onIntent(AccountsIntent.ArchiveAccount(1L))

        assertEquals("Не удалось архивировать счёт.", viewModel.state.value.errorMessage)
    }
}
