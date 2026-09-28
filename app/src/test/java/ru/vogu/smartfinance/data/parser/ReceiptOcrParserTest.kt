package ru.vogu.smartfinance.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrParserTest {

    private val parser = ReceiptOcrParser()

    @Test
    fun `lines with a price are kept`() {
        val lines = listOf("Молоко Простоквашино 3.2% 1л 89.90", "Хлеб Бородинский 350г 45,50")

        val result = parser.extractCandidateLines(lines)

        assertEquals(2, result.size)
    }

    @Test
    fun `service lines without a price signal are dropped`() {
        val lines = listOf(
            "ООО Ромашка",
            "ИНН 7712345678",
            "Кассир: Иванова И.И.",
            "СНО: ОСН",
            "ФН № 9999078912345",
            "ФД № 12345",
            "ФП № 987654321",
            "г. Москва, ул. Ленина, д. 1",
            "www.example.ru",
            "test@example.ru",
            "+7 999 123 45 67",
            "ИТОГО 135.40",
            "НАЛИЧНЫМИ 135.40",
            "СДАЧА 0.00",
            "НДС 20% 22.57"
        )

        val result = parser.extractCandidateLines(lines)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank and too short lines are dropped even with digits`() {
        val lines = listOf("", "   ", "1.5")

        val result = parser.extractCandidateLines(lines)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `lines without a recognizable price are dropped`() {
        val lines = listOf("Пакет x3 шт", "Скидка по карте")

        val result = parser.extractCandidateLines(lines)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed receipt keeps only item lines in original order`() {
        val lines = listOf(
            "ООО Ромашка",
            "Молоко 89.90",
            "ИНН 7712345678",
            "Хлеб 45.50",
            "ИТОГО 135.40"
        )

        val result = parser.extractCandidateLines(lines)

        assertEquals(listOf("Молоко 89.90", "Хлеб 45.50"), result)
    }
}
