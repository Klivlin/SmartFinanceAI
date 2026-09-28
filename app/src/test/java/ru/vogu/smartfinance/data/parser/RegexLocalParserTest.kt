package ru.vogu.smartfinance.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.vogu.smartfinance.core.database.entity.TransactionType
import java.math.BigDecimal

class RegexLocalParserTest {

    private val parser = RegexLocalParser()

    @Test
    fun `trailing amount pattern - taxi`() {
        val result = parser.tryParse("такси 450")
        assertNotNull(result)
        assertEquals(BigDecimal("450"), result!!.amount)
        assertEquals("Такси", result.title)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertNull(result.accountHint)
    }

    @Test
    fun `leading amount pattern - pyaterochka`() {
        val result = parser.tryParse("1200 пятерочка")
        assertNotNull(result)
        assertEquals(BigDecimal("1200"), result!!.amount)
        assertEquals("Пятерочка", result.title)
    }

    @Test
    fun `trailing amount with account hint`() {
        val result = parser.tryParse("кофе 250 с карты")
        assertNotNull(result)
        assertEquals(BigDecimal("250"), result!!.amount)
        assertEquals("Кофе", result.title)
        assertEquals("card", result.accountHint?.normalizedKeyword)
    }

    @Test
    fun `cash account hint is extracted`() {
        val result = parser.tryParse("такси 450 наличкой")
        assertNotNull(result)
        assertEquals("cash", result!!.accountHint?.normalizedKeyword)
        assertEquals("Такси", result.title)
    }

    @Test
    fun `decimal amount with comma is parsed`() {
        val result = parser.tryParse("кофе 149,90")
        assertNotNull(result)
        assertEquals(BigDecimal("149.90"), result!!.amount)
    }

    @Test
    fun `no number present returns null`() {
        assertNull(parser.tryParse("просто текст без суммы"))
    }

    @Test
    fun `ambiguous two numbers returns null`() {
        assertNull(parser.tryParse("500 кофе 100"))
    }

    @Test
    fun `negative amount is rejected`() {
        assertNull(parser.tryParse("такси -450"))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(parser.tryParse("   "))
    }
}
