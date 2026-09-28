package ru.vogu.smartfinance.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.vogu.smartfinance.domain.model.FiscalOperationType
import java.math.BigDecimal

class FiscalQrParserTest {

    private val parser = FiscalQrParser()

    @Test
    fun `valid receipt qr is parsed`() {
        val raw = "t=20260928T1430&s=1540.50&fn=99990789&i=12345&fp=987654321&n=1"
        val result = parser.parse(raw)

        assertNotNull(result)
        assertEquals(BigDecimal("1540.50"), result!!.amount)
        assertEquals("99990789", result.fiscalDriveNumber)
        assertEquals("12345", result.fiscalDocumentNumber)
        assertEquals("987654321", result.fiscalSign)
        assertEquals(FiscalOperationType.SALE, result.operationType)
    }

    @Test
    fun `qr with seconds in timestamp is parsed`() {
        val raw = "t=20260928T143015&s=99&fn=1&i=2&fp=3&n=1"
        val result = parser.parse(raw)
        assertNotNull(result)
    }

    @Test
    fun `missing required field returns null`() {
        val raw = "t=20260928T1430&s=1540.50&fn=99990789&i=12345" // нет fp
        assertNull(parser.parse(raw))
    }

    @Test
    fun `malformed timestamp returns null`() {
        val raw = "t=not-a-date&s=100&fn=1&i=2&fp=3"
        assertNull(parser.parse(raw))
    }

    @Test
    fun `zero amount returns null`() {
        val raw = "t=20260928T1430&s=0&fn=1&i=2&fp=3"
        assertNull(parser.parse(raw))
    }

    @Test
    fun `default operation type is sale when n is absent`() {
        val raw = "t=20260928T1430&s=100&fn=1&i=2&fp=3"
        val result = parser.parse(raw)
        assertEquals(FiscalOperationType.SALE, result!!.operationType)
    }

    @Test
    fun `refund operation type is parsed`() {
        val raw = "t=20260928T1430&s=100&fn=1&i=2&fp=3&n=2"
        val result = parser.parse(raw)
        assertEquals(FiscalOperationType.SALE_RETURN, result!!.operationType)
    }
}
