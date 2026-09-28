package ru.vogu.smartfinance.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.vogu.smartfinance.core.common.NetworkMonitor
import ru.vogu.smartfinance.core.common.Resource
import ru.vogu.smartfinance.core.database.dao.CategoryDao
import ru.vogu.smartfinance.core.database.entity.CategoryEntity
import ru.vogu.smartfinance.core.network.GeminiClient
import ru.vogu.smartfinance.core.security.PiiFilter
import ru.vogu.smartfinance.data.parser.FiscalQrParser
import ru.vogu.smartfinance.data.parser.ReceiptOcrParser
import ru.vogu.smartfinance.domain.model.FiscalOperationType
import ru.vogu.smartfinance.domain.model.FiscalReceiptData
import ru.vogu.smartfinance.domain.model.ParsedReceiptItem
import java.math.BigDecimal
import java.time.Instant

class ReceiptRepositoryImplTest {

    private val fiscalQrParser: FiscalQrParser = mockk()
    private val receiptOcrParser: ReceiptOcrParser = mockk()
    private val piiFilter = PiiFilter()
    private val categoryDao: CategoryDao = mockk()
    private val geminiClient: GeminiClient = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    private lateinit var repository: ReceiptRepositoryImpl

    @Before
    fun setUp() {
        repository = ReceiptRepositoryImpl(
            fiscalQrParser = fiscalQrParser,
            receiptOcrParser = receiptOcrParser,
            piiFilter = piiFilter,
            categoryDao = categoryDao,
            geminiClient = geminiClient,
            networkMonitor = networkMonitor
        )
    }

    @Test
    fun `parseFiscalQr delegates directly to FiscalQrParser`() {
        val receipt = FiscalReceiptData(
            timestamp = Instant.now(),
            amount = BigDecimal("1540.50"),
            fiscalDriveNumber = "99990789",
            fiscalDocumentNumber = "12345",
            fiscalSign = "987654321",
            operationType = FiscalOperationType.SALE,
            rawQrValue = "t=20260928T1430&s=1540.50&fn=99990789&i=12345&fp=987654321&n=1"
        )
        every { fiscalQrParser.parse(receipt.rawQrValue) } returns receipt

        val result = repository.parseFiscalQr(receipt.rawQrValue)

        assertEquals(receipt, result)
    }

    @Test
    fun `no candidate lines returns an error without checking network or calling gemini`() = runTest {
        every { receiptOcrParser.extractCandidateLines(any()) } returns emptyList()

        val result = repository.parseOcrLines(listOf("нечитаемый мусор"))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { geminiClient.parseReceiptItems(any(), any()) }
        verify(exactly = 0) { networkMonitor.isOnline() }
    }

    @Test
    fun `candidate lines but offline returns an error without calling gemini`() = runTest {
        every { receiptOcrParser.extractCandidateLines(any()) } returns listOf("Молоко 89.90")
        every { networkMonitor.isOnline() } returns false

        val result = repository.parseOcrLines(listOf("Молоко 89.90"))

        assertTrue(result is Resource.Error)
        coVerify(exactly = 0) { geminiClient.parseReceiptItems(any(), any()) }
    }

    @Test
    fun `candidate lines online sanitizes each line and forwards categories to gemini`() = runTest {
        every { receiptOcrParser.extractCandidateLines(any()) } returns listOf("Молоко 89.90", "Карта 4276 1234 5678 9012")
        every { networkMonitor.isOnline() } returns true
        coEvery { categoryDao.getAllOnce() } returns listOf(
            CategoryEntity(id = 1L, name = "Еда", iconName = "restaurant", colorHex = "#FF7043", isIncomeCategory = false, isSystem = true, sortOrder = 1)
        )
        val linesSlot = slot<List<String>>()
        coEvery { geminiClient.parseReceiptItems(capture(linesSlot), any()) } returns Resource.Success(
            listOf(ParsedReceiptItem(name = "Молоко", amount = BigDecimal("89.90"), categoryId = 1L))
        )

        val result = repository.parseOcrLines(listOf("Молоко 89.90", "Карта 4276 1234 5678 9012"))

        assertTrue(result is Resource.Success)
        // Номер карты (PII) должен быть вырезан до отправки в Gemini — CLAUDE.md §2.6.
        assertTrue(linesSlot.captured.none { it.contains("4276") })
        assertTrue(linesSlot.captured.any { it.contains("[КАРТА]") })
    }
}
