package ru.vogu.smartfinance.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiFilterTest {

    private val filter = PiiFilter()

    @Test
    fun `card number is redacted`() {
        val result = filter.sanitize("оплатил картой 4276 1234 5678 9012 за такси")
        assertTrue(PiiCategory.CARD_NUMBER in result.redactedCategories)
        assertFalse(result.sanitizedText.contains("4276"))
    }

    @Test
    fun `phone number is redacted`() {
        val result = filter.sanitize("перевёл другу на +7 912 345 67 89 тысячу")
        assertTrue(PiiCategory.PHONE in result.redactedCategories)
        assertFalse(result.sanitizedText.contains("912"))
    }

    @Test
    fun `cvv is redacted`() {
        val result = filter.sanitize("cvv 123 не сохраняй нигде")
        assertTrue(PiiCategory.CVV in result.redactedCategories)
        assertFalse(result.sanitizedText.contains("123"))
    }

    @Test
    fun `balance is redacted but ordinary amount is not`() {
        val result = filter.sanitize("баланс 154000, потратил кофе 250")
        assertTrue(PiiCategory.BALANCE in result.redactedCategories)
        assertFalse(result.sanitizedText.contains("154000"))
        // обычная сумма транзакции не должна вырезаться
        assertTrue(result.sanitizedText.contains("250"))
    }

    @Test
    fun `plain expense text has no pii`() {
        val result = filter.sanitize("такси 450")
        assertFalse(result.hasPii)
        assertEquals("такси 450", result.sanitizedText)
    }
}
