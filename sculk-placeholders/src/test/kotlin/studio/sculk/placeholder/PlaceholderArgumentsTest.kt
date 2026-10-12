package studio.sculk.placeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.execution.SculkScope
import studio.sculk.failureOrNull
import studio.sculk.getOrNull
import studio.sculk.testkit.FakeExecutor
import studio.sculk.text.Text

class PlaceholderArgumentsTest {
    private val scope = SculkScope.root("plugin", FakeExecutor()) {}
    private val placeholders = PlaceholderRegistry(scope)
    private val text = Text()
    private val steve = Viewer("Steve")

    private val stat = PlaceholderArgument.choice("stat", mapOf("kills" to 12, "deaths" to 3))
    private val width = PlaceholderArgument.integer("width", min = 1, max = 8).optional(1)

    private fun statistics() = placeholders.audience("stats:stat", arguments = listOf(stat, width)) {
        this[stat].toString().padStart(this[width], '0')
    }

    @Test
    fun `arguments are read in order and validated`() {
        statistics()
        assertEquals(
            "kills 12, deaths 0003",
            text.render("kills <stats_stat:kills>, deaths <stats_stat:DEATHS:4>", placeholders.resolver(steve)).plain(),
        )
    }

    @Test
    fun `an optional argument left out reads its default`() {
        val placeholder = statistics()
        assertEquals("3", placeholder.value(steve, listOf("deaths")).getOrNull())
    }

    @Test
    fun `a tag with an invalid, missing or extra argument is left as written`() {
        statistics()
        val template = "<stats_stat:assists> <stats_stat> <stats_stat:kills:2:extra> <stats_stat:kills:9>"
        assertEquals(template, text.render(template, placeholders.resolver(steve)).plain())
    }

    @Test
    fun `reading a value with invalid arguments is a failure naming the argument and what it expects`() {
        val placeholder = statistics()
        val invalid = placeholder.value(steve, listOf("assists")).failureOrNull()
        assertEquals("<stats_stat> argument <stat>: expected one of kills, deaths, found 'assists'.", invalid?.describe())
        val missing = placeholder.value(steve).failureOrNull()
        assertEquals("<stats_stat> argument <stat>: expected one of kills, deaths, found nothing.", missing?.describe())
        val extra = placeholder.value(steve, listOf("kills", "2", "3")).failureOrNull()
        assertEquals("<stats_stat>: expected at most 2 arguments, found 3.", extra?.describe())
    }

    @Test
    fun `values are kept per argument list`() {
        var computed = 0
        val doubled = PlaceholderArgument.integer("value")
        val placeholder = placeholders.global("math:double", Refresh.UntilInvalidated, arguments = listOf(doubled)) {
            computed++
            this[doubled] * 2
        }
        assertEquals(listOf(4, 6, 4), listOf("2", "3", "2").map { placeholder.value(listOf(it)).getOrNull() })
        assertEquals(2, computed)
    }

    @Test
    fun `a text argument can be limited to a pattern`() {
        val name = PlaceholderArgument.text("name", Regex("[a-z]+"))
        placeholders.global("debug:upper", arguments = listOf(name)) { this[name].uppercase() }
        assertEquals("ABC <debug_upper:a1>", text.render("<debug_upper:abc> <debug_upper:a1>", placeholders.resolver()).plain())
    }

    @Test
    fun `a custom argument parses with its own function`() {
        val hex = PlaceholderArgument.of("hex", "a hexadecimal number") { it.toIntOrNull(16) }
        placeholders.global("debug:decimal", arguments = listOf(hex)) { this[hex] }
        assertEquals("255", text.render("<debug_decimal:ff>", placeholders.resolver()).plain())
    }

    @Test
    fun `declaring a required argument after an optional one, or two with one name, is a wiring bug`() {
        assertThrows<IllegalArgumentException> { placeholders.global("a:b", arguments = listOf(width, stat)) { 1 } }
        assertThrows<IllegalArgumentException> { placeholders.global("a:c", arguments = listOf(stat, stat)) { 1 } }
    }

    @Test
    fun `reading an argument the placeholder did not declare is a wiring bug`() {
        val placeholder = placeholders.global("a:b") { this[stat] }
        assertThrows<IllegalStateException> { placeholder.value() }
    }

    @Test
    fun `a malformed argument declaration is refused`() {
        assertThrows<IllegalArgumentException> { PlaceholderArgument.text("Bad Name") }
        assertThrows<IllegalArgumentException> { PlaceholderArgument.integer("n", min = 5, max = 1) }
        assertThrows<IllegalArgumentException> { PlaceholderArgument.choice("c", mapOf("a" to 1, "A" to 2)) }
    }
}
