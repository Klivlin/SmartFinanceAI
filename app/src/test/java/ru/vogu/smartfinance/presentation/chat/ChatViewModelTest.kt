package ru.vogu.smartfinance.presentation.chat

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.Category
import ru.vogu.smartfinance.domain.model.DashboardSummary
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.CategoryRepository
import ru.vogu.smartfinance.domain.repository.ChatHistoryRepository
import ru.vogu.smartfinance.domain.repository.OnboardingRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import ru.vogu.smartfinance.domain.usecase.ObserveDashboardSummaryUseCase
import ru.vogu.smartfinance.domain.usecase.ProcessNliInputUseCase
import ru.vogu.smartfinance.domain.usecase.UndoTransactionUseCase
import ru.vogu.smartfinance.domain.usecase.UpdateTransactionUseCase
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val processNliInputUseCase: ProcessNliInputUseCase = mockk()
    private val undoTransactionUseCase: UndoTransactionUseCase = mockk()
    private val updateTransactionUseCase: UpdateTransactionUseCase = mockk()
    private val observeDashboardSummaryUseCase: ObserveDashboardSummaryUseCase = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val accountRepository: AccountRepository = mockk()
    private val chatHistoryRepository: ChatHistoryRepository = mockk()
    private val onboardingRepository: OnboardingRepository = mockk()

    private val recentTransactions = MutableStateFlow<List<Transaction>>(emptyList())

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

    private val fakeCategory = Category(
        id = 6L,
        name = "Здоровье",
        iconName = "favorite",
        colorHex = "#66BB6A",
        monthlyBudget = null,
        isIncomeCategory = false,
        isSystem = true,
        sortOrder = 6
    )

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { transactionRepository.observeRecentByEntryOrder(any()) } returns recentTransactions
        every { categoryRepository.observeAll() } returns flowOf(listOf(fakeCategory))
        every { accountRepository.observeAll() } returns flowOf(listOf(fakeAccount))
        every { chatHistoryRepository.clearedAt } returns flowOf(null)
        every { onboardingRepository.isInitialBalancePrompted } returns flowOf(true)
        every { observeDashboardSummaryUseCase() } returns flowOf(
            DashboardSummary(
                totalBalance = BigDecimal("1000"),
                baseCurrency = "RUB",
                spentToday = BigDecimal.ZERO,
                spentThisMonth = BigDecimal.ZERO,
                monthlyBudgetTotal = null
            )
        )

        viewModel = ChatViewModel(
            processNliInputUseCase = processNliInputUseCase,
            undoTransactionUseCase = undoTransactionUseCase,
            updateTransactionUseCase = updateTransactionUseCase,
            observeDashboardSummaryUseCase = observeDashboardSummaryUseCase,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            chatHistoryRepository = chatHistoryRepository,
            onboardingRepository = onboardingRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun transaction(id: Long, rawInputText: String?, createdAt: Instant) = Transaction(
        id = id,
        accountId = fakeAccount.id,
        toAccountId = null,
        categoryId = fakeCategory.id,
        amount = BigDecimal("3500"),
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = BigDecimal("3500"),
        transactionType = TransactionType.EXPENSE,
        title = "Спортзал",
        notes = null,
        timestamp = createdAt,
        source = TransactionSource.NLI,
        rawInputText = rawInputText,
        transferFeeAmount = null,
        recurringRuleId = null,
        isSplit = false,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    /** Держит upstream-комбинацию `state` живой на время теста (StateFlow с WhileSubscribed). */
    private fun TestScope.keepStateAlive() {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    @Test
    fun `two transactions with the same rawInputText collapse into one user bubble and two cards`() = runTest {
        keepStateAlive()
        val now = Instant.now()
        recentTransactions.value = listOf(
            transaction(1L, "вчера 3500 за спортзал, а сегодня 1000 на сбер", now),
            transaction(2L, "вчера 3500 за спортзал, а сегодня 1000 на сбер", now.plusSeconds(1))
        )

        val messages = viewModel.state.first { it.messages.isNotEmpty() }.messages

        assertEquals(3, messages.size)
        assertTrue(messages[0] is ChatMessage.UserText)
        assertTrue(messages[1] is ChatMessage.TransactionCard)
        assertTrue(messages[2] is ChatMessage.TransactionCard)
    }

    @Test
    fun `transaction without rawInputText renders as a card with no preceding bubble`() = runTest {
        keepStateAlive()
        recentTransactions.value = listOf(transaction(1L, null, Instant.now()))

        val messages = viewModel.state.first { it.messages.isNotEmpty() }.messages

        assertEquals(1, messages.size)
        assertTrue(messages[0] is ChatMessage.TransactionCard)
    }

    @Test
    fun `sending a message calls the use case and clears the sending flag on success`() = runTest {
        keepStateAlive()
        coEvery { processNliInputUseCase("такси 450") } returns Resource.Success(listOf(transaction(1L, "такси 450", Instant.now())))

        viewModel.onIntent(ChatIntent.InputTextChanged("такси 450"))
        viewModel.onIntent(ChatIntent.SendMessage)

        coVerify(exactly = 1) { processNliInputUseCase("такси 450") }
        assertFalse(viewModel.state.value.isSending)
        assertEquals("", viewModel.state.value.inputText)
    }

    @Test
    fun `failed send surfaces the error message and clears the sending flag`() = runTest {
        keepStateAlive()
        coEvery { processNliInputUseCase(any()) } returns Resource.Error("Нет сети")

        viewModel.onIntent(ChatIntent.InputTextChanged("что-то непонятное"))
        viewModel.onIntent(ChatIntent.SendMessage)

        assertEquals("Нет сети", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isSending)
    }

    @Test
    fun `blank input is not sent`() = runTest {
        keepStateAlive()
        viewModel.onIntent(ChatIntent.InputTextChanged("   "))
        viewModel.onIntent(ChatIntent.SendMessage)

        coVerify(exactly = 0) { processNliInputUseCase(any()) }
    }

    @Test
    fun `confirm marks the matching card as confirmed without touching the repository`() = runTest {
        keepStateAlive()
        recentTransactions.value = listOf(transaction(1L, null, Instant.now()))

        viewModel.onIntent(ChatIntent.ConfirmTransaction(1L))

        val card = viewModel.state.value.messages.filterIsInstance<ChatMessage.TransactionCard>().first()
        assertTrue(card.isConfirmed)
    }

    @Test
    fun `undo deletes the transaction via the use case`() = runTest {
        keepStateAlive()
        coEvery { transactionRepository.getById(1L) } returns transaction(1L, null, Instant.now())
        coEvery { undoTransactionUseCase(1L) } returns Resource.Success(Unit)

        viewModel.onIntent(ChatIntent.UndoTransaction(1L))

        coVerify(exactly = 1) { undoTransactionUseCase(1L) }
    }

    @Test
    fun `undo failure surfaces the error message`() = runTest {
        keepStateAlive()
        coEvery { transactionRepository.getById(1L) } returns transaction(1L, null, Instant.now())
        coEvery { undoTransactionUseCase(1L) } returns Resource.Error("Транзакция не найдена.")

        viewModel.onIntent(ChatIntent.UndoTransaction(1L))

        assertEquals("Транзакция не найдена.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `undo emits the deleted transaction so it can be restored`() = runTest {
        keepStateAlive()
        val tx = transaction(1L, null, Instant.now())
        coEvery { transactionRepository.getById(1L) } returns tx
        coEvery { undoTransactionUseCase(1L) } returns Resource.Success(Unit)

        viewModel.onIntent(ChatIntent.UndoTransaction(1L))

        // UnconfinedTestDispatcher запускает viewModelScope.launch эагерно — к этому моменту
        // эффект уже лежит в канале, буферизованном достаточно для одного элемента.
        val effect = viewModel.effect.first()
        assertTrue(effect is ChatEffect.TransactionDeleted && effect.transaction == tx)
    }

    @Test
    fun `restoring a transaction re-creates it with the same fields`() = runTest {
        keepStateAlive()
        val tx = transaction(7L, null, Instant.now())
        coEvery {
            transactionRepository.createTransaction(
                accountId = tx.accountId, toAccountId = tx.toAccountId, categoryId = tx.categoryId,
                amount = tx.amount, transactionType = tx.transactionType, title = tx.title, notes = tx.notes,
                timestamp = tx.timestamp, source = tx.source, rawInputText = tx.rawInputText,
                originalCurrency = tx.originalCurrency, transferFeeAmount = tx.transferFeeAmount,
                recurringRuleId = tx.recurringRuleId, splitItems = tx.splitItems
            )
        } returns tx

        viewModel.onIntent(ChatIntent.RestoreTransaction(tx))

        coVerify(exactly = 1) {
            transactionRepository.createTransaction(
                accountId = tx.accountId, toAccountId = tx.toAccountId, categoryId = tx.categoryId,
                amount = tx.amount, transactionType = tx.transactionType, title = tx.title, notes = tx.notes,
                timestamp = tx.timestamp, source = tx.source, rawInputText = tx.rawInputText,
                originalCurrency = tx.originalCurrency, transferFeeAmount = tx.transferFeeAmount,
                recurringRuleId = tx.recurringRuleId, splitItems = tx.splitItems
            )
        }
    }

    @Test
    fun `edit request loads the transaction into state`() = runTest {
        keepStateAlive()
        val tx = transaction(5L, null, Instant.now())
        coEvery { transactionRepository.getById(5L) } returns tx

        viewModel.onIntent(ChatIntent.EditTransactionRequested(5L))

        assertEquals(tx, viewModel.state.value.editingTransaction)
    }

    @Test
    fun `edit request for a missing transaction surfaces an error instead of opening the sheet`() = runTest {
        keepStateAlive()
        coEvery { transactionRepository.getById(99L) } returns null

        viewModel.onIntent(ChatIntent.EditTransactionRequested(99L))

        assertNull(viewModel.state.value.editingTransaction)
        assertEquals("Операция не найдена — возможно, уже удалена.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `saving an edited transaction dismisses the sheet on success`() = runTest {
        keepStateAlive()
        val tx = transaction(5L, null, Instant.now())
        coEvery { updateTransactionUseCase(tx) } returns Resource.Success(tx)

        viewModel.onIntent(ChatIntent.SaveEditedTransaction(tx))

        assertNull(viewModel.state.value.editingTransaction)
    }
}
