package ru.vogu.smartfinance.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.entity.AccountType
import ru.vogu.smartfinance.core.database.entity.TransactionSource
import ru.vogu.smartfinance.core.database.entity.TransactionType
import ru.vogu.smartfinance.domain.model.Account
import ru.vogu.smartfinance.domain.model.FiscalOperationType
import ru.vogu.smartfinance.domain.model.FiscalReceiptData
import ru.vogu.smartfinance.domain.model.ParsedReceiptItem
import ru.vogu.smartfinance.domain.model.SplitItem
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.repository.AccountRepository
import ru.vogu.smartfinance.domain.repository.ReceiptRepository
import ru.vogu.smartfinance.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant

class ParseReceiptUseCaseTest {

    private val receiptRepository: ReceiptRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val accountRepository: AccountRepository = mockk()

    private lateinit var useCase: ParseReceiptUseCase

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
        useCase = ParseReceiptUseCase(receiptRepository, transactionRepository, accountRepository)
        coEvery { accountRepository.findBestMatch(null) } returns fakeAccount
    }

    private fun fakeTransaction(amount: BigDecimal, categoryId: Long?, isSplit: Boolean = false) = Transaction(
        id = 1L,
        accountId = fakeAccount.id,
        toAccountId = null,
        categoryId = categoryId,
        amount = amount,
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = amount,
        transactionType = TransactionType.EXPENSE,
        title = "Тест",
        notes = null,
        timestamp = Instant.now(),
        source = TransactionSource.QR,
        rawInputText = null,
        transferFeeAmount = null,
        recurringRuleId = null,
        isSplit = isSplit,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `unrecognized QR returns an error without touching the repositories`() = runTest {
        every { receiptRepository.parseFiscalQr("garbage") } returns null

        val result = useCase.fromFiscalQr("garbage")

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = any(), rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        }
    }

    @Test
    fun `valid QR creates a single non-split EXPENSE transaction for the receipt total`() = runTest {
        val receipt = FiscalReceiptData(
            timestamp = Instant.now(),
            amount = BigDecimal("1540.50"),
            fiscalDriveNumber = "99990789",
            fiscalDocumentNumber = "12345",
            fiscalSign = "987654321",
            operationType = FiscalOperationType.SALE,
            rawQrValue = "t=20260928T1430&s=1540.50&fn=99990789&i=12345&fp=987654321&n=1"
        )
        every { receiptRepository.parseFiscalQr(receipt.rawQrValue) } returns receipt

        val amountSlot = slot<BigDecimal>()
        val splitItemsSlot = slot<List<SplitItem>>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = capture(amountSlot), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.QR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = capture(splitItemsSlot)
            )
        } answers { fakeTransaction(receipt.amount, categoryId = 9L) }

        val result = useCase.fromFiscalQr(receipt.rawQrValue)

        assertTrue(result is Resource.Success)
        assertEquals(receipt.amount, amountSlot.captured)
        assertTrue(splitItemsSlot.captured.isEmpty())
    }

    @Test
    fun `empty OCR result surfaces the underlying error`() = runTest {
        coEvery { receiptRepository.parseOcrLines(any()) } returns Resource.Error("Не удалось найти позиции.")

        val result = useCase.fromOcrLines(listOf("нечитаемая строка"))

        assertTrue(result is Resource.Error)
        assertEquals("Не удалось найти позиции.", (result as Resource.Error).message)
    }

    @Test
    fun `single item receipt creates one non-split transaction with that item's category`() = runTest {
        val item = ParsedReceiptItem(name = "Такси до дома", amount = BigDecimal("450"), categoryId = 2L)
        coEvery { receiptRepository.parseOcrLines(any()) } returns Resource.Success(listOf(item))

        val categoryIdSlot = slot<Long?>()
        val splitItemsSlot = slot<List<SplitItem>>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = captureNullable(categoryIdSlot),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.OCR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = capture(splitItemsSlot)
            )
        } answers { fakeTransaction(item.amount, categoryId = item.categoryId) }

        val result = useCase.fromOcrLines(listOf("Такси до дома 450.00"))

        assertTrue(result is Resource.Success)
        assertEquals(2L, categoryIdSlot.captured)
        assertTrue(splitItemsSlot.captured.isEmpty())
    }

    @Test
    fun `multi item receipt creates a split transaction with summed total and null parent category`() = runTest {
        val items = listOf(
            ParsedReceiptItem(name = "Молоко", amount = BigDecimal("89.90"), categoryId = 1L),
            ParsedReceiptItem(name = "Хлеб", amount = BigDecimal("45.50"), categoryId = 1L)
        )
        coEvery { receiptRepository.parseOcrLines(any()) } returns Resource.Success(items)

        val amountSlot = slot<BigDecimal>()
        val categoryIdSlot = slot<Long?>()
        val splitItemsSlot = slot<List<SplitItem>>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = captureNullable(categoryIdSlot),
                amount = capture(amountSlot), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.OCR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = capture(splitItemsSlot)
            )
        } answers { fakeTransaction(BigDecimal("135.40"), categoryId = null, isSplit = true) }

        val result = useCase.fromOcrLines(listOf("Молоко 89.90", "Хлеб 45.50"))

        assertTrue(result is Resource.Success)
        assertEquals(BigDecimal("135.40"), amountSlot.captured)
        assertNull(categoryIdSlot.captured)
        assertEquals(2, splitItemsSlot.captured.size)
    }

    @Test
    fun `qr plus ocr prefers the per-item split and timestamps it at scan time, not the receipt's own date`() = runTest {
        // Чат показывает только последние N операций по времени (CLAUDE.md §5.1) — старый
        // физический чек со своей датой из QR не должен "исчезать" из ленты из-за этого.
        val fiscalTimestamp = Instant.parse("2026-09-11T10:20:00Z")
        val beforeCall = Instant.now()
        val receipt = FiscalReceiptData(
            timestamp = fiscalTimestamp,
            amount = BigDecimal("135.40"),
            fiscalDriveNumber = "99990789",
            fiscalDocumentNumber = "12345",
            fiscalSign = "987654321",
            operationType = FiscalOperationType.SALE,
            rawQrValue = "t=20260928T1430&s=135.40&fn=99990789&i=12345&fp=987654321&n=1"
        )
        val items = listOf(
            ParsedReceiptItem(name = "Молоко", amount = BigDecimal("89.90"), categoryId = 1L),
            ParsedReceiptItem(name = "Хлеб", amount = BigDecimal("45.50"), categoryId = 1L)
        )
        every { receiptRepository.parseFiscalQr(receipt.rawQrValue) } returns receipt
        coEvery { receiptRepository.parseOcrLines(listOf("Молоко 89.90", "Хлеб 45.50")) } returns Resource.Success(items)

        val timestampSlot = slot<Instant>()
        val splitItemsSlot = slot<List<SplitItem>>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = capture(timestampSlot), source = TransactionSource.OCR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = capture(splitItemsSlot)
            )
        } answers { fakeTransaction(receipt.amount, categoryId = null, isSplit = true) }

        val result = useCase.fromQrAndOcr(receipt.rawQrValue, listOf("Молоко 89.90", "Хлеб 45.50"))

        assertTrue(result is Resource.Success)
        assertTrue(timestampSlot.captured.isAfter(beforeCall) || timestampSlot.captured == beforeCall)
        assertTrue(timestampSlot.captured != fiscalTimestamp)
        assertEquals(2, splitItemsSlot.captured.size)
        coVerify(exactly = 0) {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.QR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        }
    }

    @Test
    fun `qr plus ocr falls back to the lump QR transaction when no items are recognized`() = runTest {
        val receipt = FiscalReceiptData(
            timestamp = Instant.now(),
            amount = BigDecimal("1540.50"),
            fiscalDriveNumber = "99990789",
            fiscalDocumentNumber = "12345",
            fiscalSign = "987654321",
            operationType = FiscalOperationType.SALE,
            rawQrValue = "t=20260928T1430&s=1540.50&fn=99990789&i=12345&fp=987654321&n=1"
        )
        every { receiptRepository.parseFiscalQr(receipt.rawQrValue) } returns receipt
        coEvery { receiptRepository.parseOcrLines(any()) } returns Resource.Error("Плохое освещение.")

        val amountSlot = slot<BigDecimal>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = capture(amountSlot), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.QR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        } answers { fakeTransaction(receipt.amount, categoryId = 9L) }

        val result = useCase.fromQrAndOcr(receipt.rawQrValue, listOf("нечитаемая строка"))

        assertTrue(result is Resource.Success)
        assertEquals(receipt.amount, amountSlot.captured)
    }

    @Test
    fun `qr plus ocr with an unrecognized QR and no ocr items surfaces an error`() = runTest {
        every { receiptRepository.parseFiscalQr("garbage") } returns null

        val result = useCase.fromQrAndOcr("garbage", emptyList())

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { receiptRepository.parseOcrLines(any()) }
    }

    @Test
    fun `ocr items summing to less than the qr total get topped up with a remainder split item`() = runTest {
        // Реальный кейс: OCR/Gemini прочитали только 2 из 3 позиций чека (72.20 + 100.00),
        // а по QR реальная сумма чека — 194.30. Разница (22.10) не должна просто теряться.
        val receipt = FiscalReceiptData(
            timestamp = Instant.now(),
            amount = BigDecimal("194.30"),
            fiscalDriveNumber = "99990789",
            fiscalDocumentNumber = "12345",
            fiscalSign = "987654321",
            operationType = FiscalOperationType.SALE,
            rawQrValue = "t=20260928T1430&s=194.30&fn=99990789&i=12345&fp=987654321&n=1"
        )
        val items = listOf(
            ParsedReceiptItem(name = "Товар", amount = BigDecimal("72.20"), categoryId = 1L),
            ParsedReceiptItem(name = "Товар", amount = BigDecimal("100.00"), categoryId = 1L)
        )
        every { receiptRepository.parseFiscalQr(receipt.rawQrValue) } returns receipt
        coEvery { receiptRepository.parseOcrLines(any()) } returns Resource.Success(items)

        val amountSlot = slot<BigDecimal>()
        val splitItemsSlot = slot<List<SplitItem>>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = capture(amountSlot), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.OCR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = capture(splitItemsSlot)
            )
        } answers { fakeTransaction(receipt.amount, categoryId = null, isSplit = true) }

        val result = useCase.fromQrAndOcr(receipt.rawQrValue, listOf("товар 72.20", "товар 100.00"))

        assertTrue(result is Resource.Success)
        assertEquals(0, receipt.amount.compareTo(amountSlot.captured))
        assertEquals(3, splitItemsSlot.captured.size)
        assertEquals(0, BigDecimal("22.10").compareTo(splitItemsSlot.captured.last().amount))
    }

    @Test
    fun `ocr items summing to more than the qr total distrust the split and fall back to the qr lump`() = runTest {
        val receipt = FiscalReceiptData(
            timestamp = Instant.now(),
            amount = BigDecimal("100.00"),
            fiscalDriveNumber = "99990789",
            fiscalDocumentNumber = "12345",
            fiscalSign = "987654321",
            operationType = FiscalOperationType.SALE,
            rawQrValue = "t=20260928T1430&s=100.00&fn=99990789&i=12345&fp=987654321&n=1"
        )
        val items = listOf(
            ParsedReceiptItem(name = "Товар", amount = BigDecimal("80.00"), categoryId = 1L),
            ParsedReceiptItem(name = "Товар", amount = BigDecimal("80.00"), categoryId = 1L)
        )
        every { receiptRepository.parseFiscalQr(receipt.rawQrValue) } returns receipt
        coEvery { receiptRepository.parseOcrLines(any()) } returns Resource.Success(items)

        val amountSlot = slot<BigDecimal>()
        coEvery {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = capture(amountSlot), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.QR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        } answers { fakeTransaction(receipt.amount, categoryId = 9L) }

        val result = useCase.fromQrAndOcr(receipt.rawQrValue, listOf("товар 80.00", "товар 80.00"))

        assertTrue(result is Resource.Success)
        assertEquals(0, receipt.amount.compareTo(amountSlot.captured))
        coVerify(exactly = 0) {
            transactionRepository.createTransaction(
                accountId = any(), toAccountId = any(), categoryId = any(),
                amount = any(), transactionType = any(), title = any(), notes = any(),
                timestamp = any(), source = TransactionSource.OCR, rawInputText = any(),
                originalCurrency = any(), transferFeeAmount = any(), recurringRuleId = any(),
                splitItems = any()
            )
        }
    }
}
