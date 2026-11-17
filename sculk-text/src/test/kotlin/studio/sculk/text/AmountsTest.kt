package studio.sculk.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(SculkExperimental::class)
class AmountsTest {
    private val amounts = Amounts.DEFAULT

    private fun parsed(text: String): BigDecimal = (amounts.parse(text) as SculkResult.Success).value

    @Test
    fun `each suffix stands for the next power of a thousand`() {
        assertEquals("999", amounts.compact(999))
        assertEquals("1k", amounts.compact(1_000))
        assertEquals("1.5k", amounts.compact(1_500))
        assertEquals("2.34m", amounts.compact(2_340_000))
        assertEquals("7b", amounts.compact(7_000_000_000L))
        assertEquals("1t", amounts.compact(BigDecimal("1e12")))
        assertEquals("1qa", amounts.compact(BigDecimal("1e15")))
        assertEquals("1dc", amounts.compact(BigDecimal("1e33")))
    }

    @Test
    fun `a value past the last suffix keeps the last suffix`() {
        assertEquals("1000dc", amounts.compact(BigDecimal("1e36")))
    }

    @Test
    fun `a compact balance never shows more than it holds`() {
        assertEquals("1.99k", amounts.compact(1_999))
        assertEquals("999.99k", amounts.compact(999_999))
        assertEquals("-1.99k", amounts.compact(-1_999))
    }

    @Test
    fun `rounding up that reaches the next tier moves to its suffix`() {
        val rounded = Amounts(rounding = RoundingMode.HALF_UP)
        assertEquals("1m", rounded.compact(999_999))
        assertEquals("2k", rounded.compact(1_999))
    }

    @Test
    fun `decimals follow the chosen precision and drop trailing zeros`() {
        assertEquals("1.234k", Amounts(maxFractionDigits = 3).compact(1_234))
        assertEquals("1k", Amounts(maxFractionDigits = 0).compact(1_999))
        assertEquals("0.1", amounts.compact(0.1))
        assertEquals("0", amounts.compact(-0.001))
    }

    @Test
    fun `reading is exact and ignores the suffix's case`() {
        assertEquals(BigDecimal("1500"), parsed("1.5k"))
        assertEquals(BigDecimal("1500"), parsed("1.5K"))
        assertEquals(BigDecimal("500000"), parsed(".5m"))
        assertEquals(BigDecimal("-2000000000"), parsed("-2B"))
        assertEquals(BigDecimal("250"), parsed("250"))
        assertEquals(BigDecimal("1000000000000000"), parsed("1qa"))
        assertEquals(0, BigDecimal("0.1").compareTo(parsed("0.1")))
    }

    @Test
    fun `text that is not an amount is refused`() {
        listOf("", "k", "1.5x", "1,000", "1 k", "1e3", "abc", "--1", "1..5k").forEach { text ->
            assertTrue(amounts.parse(text) is SculkFailure, "accepted '$text'")
        }
    }

    @Test
    fun `what compact writes, parse reads back`() {
        listOf(0, 7, 1_000, 1_500, 2_340_000, 999_990_000).forEach { value ->
            assertEquals(0, BigDecimal(value).compareTo(parsed(amounts.compact(value))), "round trip of $value")
        }
    }

    @Test
    fun `custom suffixes must be letters and differ ignoring case`() {
        assertEquals("3K", Amounts(listOf("K", "M")).compact(3_000))
        assertThrows<IllegalArgumentException> { Amounts(listOf("k", "K")) }
        assertThrows<IllegalArgumentException> { Amounts(listOf("1k")) }
        assertThrows<IllegalArgumentException> { amounts.compact(Double.NaN) }
    }

    @Test
    fun `any number can be written compactly with the default amounts`() {
        assertEquals("1.5k", 1_500.compact())
    }
}
