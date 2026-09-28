package ru.vogu.smartfinance.presentation.scanner

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import ru.vogu.smartfinance.domain.model.Transaction
import ru.vogu.smartfinance.domain.usecase.ParseReceiptUseCase
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val parseReceiptUseCase: ParseReceiptUseCase = mockk()
    private lateinit var viewModel: ScannerViewModel

    private val fakeTransaction = Transaction(
        id = 1L,
        accountId = 1L,
        toAccountId = null,
        categoryId = 9L,
        amount = BigDecimal("1540.50"),
        originalCurrency = "RUB",
        exchangeRate = BigDecimal.ONE,
        amountBase = BigDecimal("1540.50"),
        transactionType = TransactionType.EXPENSE,
        title = "Покупка по чеку",
        notes = null,
        timestamp = Instant.now(),
        source = TransactionSource.QR,
        rawInputText = null,
        transferFeeAmount = null,
        recurringRuleId = null,
        isSplit = false,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ScannerViewModel(parseReceiptUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful QR parse clears processing state`() = runTest {
        coEvery { parseReceiptUseCase.fromFiscalQr("qr-value") } returns Resource.Success(fakeTransaction)

        viewModel.onIntent(ScannerIntent.BarcodeDetected("qr-value"))

        coVerify(exactly = 1) { parseReceiptUseCase.fromFiscalQr("qr-value") }
        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `failed QR parse surfaces the error and does not retry the same value`() = runTest {
        coEvery { parseReceiptUseCase.fromFiscalQr("bad-qr") } returns Resource.Error("QR не распознан.")

        viewModel.onIntent(ScannerIntent.BarcodeDetected("bad-qr"))
        viewModel.onIntent(ScannerIntent.BarcodeDetected("bad-qr"))

        assertEquals("QR не распознан.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isProcessing)
        coVerify(exactly = 1) { parseReceiptUseCase.fromFiscalQr("bad-qr") }
    }

    @Test
    fun `a new QR value after a failure is still attempted`() = runTest {
        coEvery { parseReceiptUseCase.fromFiscalQr("bad-qr") } returns Resource.Error("QR не распознан.")
        coEvery { parseReceiptUseCase.fromFiscalQr("good-qr") } returns Resource.Success(fakeTransaction)

        viewModel.onIntent(ScannerIntent.BarcodeDetected("bad-qr"))
        viewModel.onIntent(ScannerIntent.BarcodeDetected("good-qr"))

        coVerify(exactly = 1) { parseReceiptUseCase.fromFiscalQr("good-qr") }
    }

    @Test
    fun `photo recognition success clears processing state`() = runTest {
        coEvery { parseReceiptUseCase.fromOcrLines(listOf("Молоко 89.90")) } returns Resource.Success(fakeTransaction)

        viewModel.onIntent(ScannerIntent.PhotoRecognized(listOf("Молоко 89.90")))

        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `photo recognition failure surfaces the error`() = runTest {
        coEvery { parseReceiptUseCase.fromOcrLines(any()) } returns Resource.Error("Не удалось распознать позиции.")

        viewModel.onIntent(ScannerIntent.PhotoRecognized(listOf("мусор")))

        assertEquals("Не удалось распознать позиции.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `photo capture failure surfaces a generic retry message`() = runTest {
        viewModel.onIntent(ScannerIntent.PhotoCaptureFailed)

        assertEquals("Не удалось сделать фото. Попробуйте ещё раз.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.isProcessing)
    }

    @Test
    fun `camera permission denied surfaces an explanatory message`() = runTest {
        viewModel.onIntent(ScannerIntent.CameraPermissionDenied)

        assertTrue(viewModel.state.value.errorMessage!!.contains("камере"))
    }

    @Test
    fun `error message shown clears the error`() = runTest {
        viewModel.onIntent(ScannerIntent.PhotoCaptureFailed)
        viewModel.onIntent(ScannerIntent.ErrorMessageShown)

        assertNull(viewModel.state.value.errorMessage)
    }
}
