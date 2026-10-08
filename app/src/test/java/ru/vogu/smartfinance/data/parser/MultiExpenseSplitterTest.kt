package ru.vogu.smartfinance.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MultiExpenseSplitterTest {

    private val splitter = MultiExpenseSplitter(RegexLocalParser())

    @Test
    fun `comma separated expenses split into two`() {
        val result = splitter.trySplit("такси 450, кофе 200")

        assertEquals(2, result?.size)
        assertEquals("Такси", result!![0].title)
        assertEquals(BigDecimal("450"), result[0].amount)
        assertEquals("Кофе", result[1].title)
        assertEquals(BigDecimal("200"), result[1].amount)
    }

    @Test
    fun `conjunction i separates expenses`() {
        val result = splitter.trySplit("кофе 200 и такси 450")

        assertEquals(2, result?.size)
        assertEquals("Кофе", result!![0].title)
        assertEquals("Такси", result[1].title)
    }

    @Test
    fun `three expenses on separate lines all split`() {
        val result = splitter.trySplit("такси 450\nкофе 200\nобед 600")

        assertEquals(3, result?.size)
    }

    @Test
    fun `single expense is not split`() {
        assertNull(splitter.trySplit("такси 450"))
    }

    @Test
    fun `unparseable segment cancels the whole split`() {
        // "хлеб и молоко 300" — "и" здесь часть названия, а не разделитель двух трат;
        // после разбиения первый фрагмент "хлеб" не содержит суммы, поэтому splitter
        // безопасно откатывается к null, а не создаёт ложную операцию "Хлеб" без суммы.
        assertNull(splitter.trySplit("хлеб и молоко 300"))
    }

    @Test
    fun `single item with comma inside account hint is not falsely split`() {
        // "такси 450 наличкой" — одна операция, без разделителей, сплиттер не должен трогать
        assertNull(splitter.trySplit("такси 450 наличкой"))
    }
}
