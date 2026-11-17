package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkResult
import studio.sculk.testkit.FakeCommandSource

class ArgumentTypeTest {
    private val console = FakeCommandSource.console()

    private fun ArgumentType<*>.accepts(text: String): Boolean = parse(text, console) is SculkResult.Success

    @Test
    fun `a number is a finite plain decimal, as Brigadier reads one`() {
        val number = Arguments.double()
        listOf("12", "-0.5", ".5", "3.", "0").forEach { assertTrue(number.accepts(it), it) }
        listOf("NaN", "Infinity", "-Infinity", "1e3", "0x1p3", "5d", "5f", "+5", "", "-", ".", "1" + "0".repeat(400)).forEach {
            assertFalse(number.accepts(it), it)
        }
    }

    @Test
    fun `a whole number is ASCII digits with an optional minus, as Brigadier reads one`() {
        listOf(Arguments.integer(), Arguments.long()).forEach { type ->
            assertTrue(type.accepts("-42"))
            assertTrue(type.accepts("007"))
            listOf("+5", "1.0", "1e3", "0x10", "", "١٢").forEach { assertFalse(type.accepts(it), it) }
        }
    }

    @Test
    fun `a boolean is lower-case true or false, since Brigadier's boolean is case-sensitive`() {
        val bool = Arguments.boolean()
        assertTrue(bool.accepts("true"))
        assertTrue(bool.accepts("false"))
        assertFalse(bool.accepts("TRUE"))
    }

    @Test
    fun `a duration too large to represent is refused rather than read as infinite`() {
        val unbounded = Arguments.duration()
        assertTrue(unbounded.accepts("36500d"))
        assertFalse(unbounded.accepts("9223372036854775807d"))
        assertFalse(unbounded.accepts("9223372036854775807s"))
    }

    @Test
    fun `choices that differ only in case are refused when declared`() {
        val failure = assertThrows<IllegalArgumentException> { Arguments.choice(mapOf("Spam" to 1, "spam" to 2)) }
        assertEquals("Choices Spam and spam differ only in case.", failure.message)
    }

    @Test
    fun `a choice is a word a server reads unquoted, matched ignoring case`() {
        assertThrows<IllegalArgumentException> { Arguments.choice(mapOf("dark red" to 1)) }
        assertThrows<IllegalArgumentException> { Arguments.choice(mapOf("#ff0000" to 1)) }
        assertTrue(Arguments.choice(mapOf("dark_red" to 1)).accepts("DARK_RED"))
    }
}
