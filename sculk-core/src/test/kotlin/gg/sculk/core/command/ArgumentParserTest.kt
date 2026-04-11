package gg.sculk.core.command

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.command.argument.BooleanParser
import gg.sculk.core.command.argument.ChoiceParser
import gg.sculk.core.command.argument.DoubleParser
import gg.sculk.core.command.argument.IntParser
import gg.sculk.core.command.argument.StringParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(SculkInternal::class)
class ArgumentParserTest {
    @Test
    fun `string parser returns input as-is`() {
        assertEquals("hello world", StringParser.parse("hello world"))
    }

    @Test
    fun `int parser parses valid integer`() {
        assertEquals(42, IntParser.parse("42"))
    }

    @Test
    fun `int parser returns null for invalid input`() {
        assertNull(IntParser.parse("notanumber"))
    }

    @Test
    fun `double parser parses valid decimal`() {
        assertEquals(3.14, DoubleParser.parse("3.14"))
    }

    @Test
    fun `boolean parser parses true variants`() {
        assertEquals(true, BooleanParser.parse("true"))
        assertEquals(true, BooleanParser.parse("yes"))
        assertEquals(true, BooleanParser.parse("1"))
    }

    @Test
    fun `boolean parser parses false variants`() {
        assertEquals(false, BooleanParser.parse("false"))
        assertEquals(false, BooleanParser.parse("no"))
        assertEquals(false, BooleanParser.parse("0"))
    }

    @Test
    fun `boolean parser returns null for invalid input`() {
        assertNull(BooleanParser.parse("maybe"))
    }

    @Test
    fun `choice parser accepts valid choice case-insensitively`() {
        val parser = ChoiceParser(listOf("red", "green", "blue"))
        assertEquals("red", parser.parse("RED"))
        assertEquals("blue", parser.parse("Blue"))
    }

    @Test
    fun `choice parser rejects invalid choice`() {
        val parser = ChoiceParser(listOf("red", "green", "blue"))
        assertNull(parser.parse("yellow"))
    }

    @Test
    fun `choice parser suggests matching completions`() {
        val parser = ChoiceParser(listOf("red", "green", "blue"))
        assertEquals(listOf("green"), parser.suggest("gr"))
    }
}
