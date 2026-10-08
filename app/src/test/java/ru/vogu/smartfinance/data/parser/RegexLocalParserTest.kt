package ru.vogu.smartfinance.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.vogu.smartfinance.core.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

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

    @Test
    fun `salary is recognized as income`() {
        val result = parser.tryParse("зарплата 50000")
        assertNotNull(result)
        assertEquals(TransactionType.INCOME, result!!.transactionType)
        assertEquals(BigDecimal("50000"), result.amount)
        assertEquals("Зарплата", result.title)
    }

    @Test
    fun `cashback with account hint is recognized as income`() {
        val result = parser.tryParse("кэшбэк 250 на сбер")
        assertNotNull(result)
        assertEquals(TransactionType.INCOME, result!!.transactionType)
        assertEquals("sber", result.accountHint?.normalizedKeyword)
    }

    @Test
    fun `ambiguous debt wording is still expense (escalates to LLM elsewhere)`() {
        val result = parser.tryParse("вернул долг 1000")
        assertNotNull(result)
        assertEquals(TransactionType.EXPENSE, result!!.transactionType)
    }

    @Test
    fun `yesterday is recognized and stripped from the title`() {
        val result = parser.tryParse("такси 450 вчера")
        assertNotNull(result)
        assertEquals(LocalDate.now().minusDays(1), result!!.date)
        assertEquals("Такси", result.title)
    }

    @Test
    fun `today keyword resolves to today's date`() {
        val result = parser.tryParse("сегодня кофе 250")
        assertNotNull(result)
        assertEquals(LocalDate.now(), result!!.date)
        assertEquals("Кофе", result.title)
    }

    @Test
    fun `day before yesterday is recognized`() {
        val result = parser.tryParse("позавчера такси 450")
        assertNotNull(result)
        assertEquals(LocalDate.now().minusDays(2), result!!.date)
        assertEquals("Такси", result.title)
    }

    @Test
    fun `date and account hint combine correctly`() {
        val result = parser.tryParse("вчера кофе 250 с карты")
        assertNotNull(result)
        assertEquals(LocalDate.now().minusDays(1), result!!.date)
        assertEquals("card", result.accountHint?.normalizedKeyword)
        assertEquals("Кофе", result.title)
    }

    @Test
    fun `no date keyword defaults to today`() {
        val result = parser.tryParse("такси 450")
        assertNotNull(result)
        assertEquals(LocalDate.now(), result!!.date)
    }

    @Test
    fun `salary abbreviation ZP is recognized as income`() {
        val result = parser.tryParse("зп 50000")
        assertNotNull(result)
        assertEquals(TransactionType.INCOME, result!!.transactionType)
    }

    @Test
    fun `crypto ticker account hint is extracted`() {
        val result = parser.tryParse("Крипта 400 юсдт")
        assertNotNull(result)
        assertEquals(BigDecimal("400"), result!!.amount)
        assertEquals("Крипта", result.title)
        assertEquals("usdt", result.accountHint?.normalizedKeyword)
    }

    @Test
    fun `crypto ticker wins over the generic crypto keyword when both are present`() {
        val result = parser.tryParse("такси 450 btc")
        assertNotNull(result)
        assertEquals("btc", result!!.accountHint?.normalizedKeyword)
        assertEquals("Такси", result.title)
    }

    @Test
    fun `generic crypto account hint is extracted when no specific ticker is mentioned`() {
        val result = parser.tryParse("такси 450 крипта")
        assertNotNull(result)
        assertEquals("crypto", result!!.accountHint?.normalizedKeyword)
        assertEquals("Такси", result.title)
    }

    @Test
    fun `account hint word alone with an amount still resolves instead of escalating to the LLM`() {
        val result = parser.tryParse("крипта 400")
        assertNotNull(result)
        assertEquals(BigDecimal("400"), result!!.amount)
        assertEquals("crypto", result.accountHint?.normalizedKeyword)
        assertEquals("Крипта", result.title)
    }

    @Test
    fun `ticker word alone with an amount still resolves instead of escalating to the LLM`() {
        val result = parser.tryParse("400 юсдт")
        assertNotNull(result)
        assertEquals(BigDecimal("400"), result!!.amount)
        assertEquals("usdt", result.accountHint?.normalizedKeyword)
        assertEquals("Юсдт", result.title)
    }
}
