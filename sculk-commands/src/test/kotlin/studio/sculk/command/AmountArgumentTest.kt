package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.testkit.FakeCommandSource
import java.math.BigDecimal

@OptIn(SculkExperimental::class)
class AmountArgumentTest {
    private val console = FakeCommandSource.console()

    @Test
    fun `an amount argument reads compact numbers exactly`() {
        val amount = Arguments.amount()
        assertEquals(BigDecimal("1500"), (amount.parse("1.5k", console) as SculkResult.Success).value)
        assertEquals(BigDecimal("2000000"), (amount.parse("2M", console) as SculkResult.Success).value)
    }

    @Test
    fun `an amount outside its range is refused with the range in the message`() {
        val price = Arguments.amount(min = BigDecimal.ONE, max = BigDecimal("1000000"))
        val failure = price.parse("2m", console) as SculkFailure.InvalidInput
        assertTrue("from 1 to 1m" in failure.expected, failure.expected)
        assertTrue(price.parse("0", console) is SculkFailure)
        assertTrue(price.parse("lots", console) is SculkFailure)
    }

    @Test
    fun `an amount travels as one word every character of which Brigadier accepts`() {
        assertEquals(ArgumentShape.WORD, Arguments.amount().shape)
    }

    @Test
    fun `a minimum above the maximum is a programming error`() {
        assertThrows<IllegalArgumentException> { Arguments.amount(min = BigDecimal.TEN, max = BigDecimal.ONE) }
    }
}
