package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandHelpTest {
    private val specs = listOf(
        command("kit") {
            permission = "sculk.kit"
            sub("give") {
                permission = "sculk.kit.admin"
                description = "Gives a kit."
                executes { }
            }
            sub("list") {
                description = "Lists your kits."
                executes { }
            }
        },
        command("warp") {
            description = "Warps you somewhere."
            executes { }
        },
    )

    private val help = CommandHelp(perPage = 8)

    private fun entriesFor(vararg permissions: String) = help.entries(specs) { it in permissions.toSet() }

    @Test
    fun `a sender without the parent permission sees nothing from that tree`() {
        val entries = entriesFor()

        assertEquals(listOf("/warp"), entries.map { it.usage })
    }

    @Test
    fun `a sender sees the subcommands they hold permission for`() {
        val entries = entriesFor("sculk.kit")

        assertEquals(listOf("/kit list", "/warp"), entries.map { it.usage })
    }

    @Test
    fun `an admin sees the whole tree`() {
        val entries = entriesFor("sculk.kit", "sculk.kit.admin")

        assertEquals(listOf("/kit give", "/kit list", "/warp"), entries.map { it.usage })
    }

    @Test
    fun `a child inherits its parent's permission when it declares none`() {
        // /kit list has no permission of its own, so it must not be visible without sculk.kit.
        assertTrue(entriesFor().none { it.usage == "/kit list" })
    }

    @Test
    fun `page one of one holds every entry`() {
        val entries = entriesFor("sculk.kit", "sculk.kit.admin")

        assertEquals(1, help.pageCount(entries))
    }

    @Test
    fun `paging slices and clamps`() {
        val many = (1..20).map { index -> command("cmd$index") { executes { } } }
        val entries = help.entries(many) { true }

        assertEquals(3, help.pageCount(entries))
        assertTrue(help.page(entries, 2).any { it.values.any { value -> value.second == "2" } })

        val clampedLow = help.page(entries, 0)
        val clampedHigh = help.page(entries, 99)

        assertTrue(clampedLow.first().values.contains("page" to "1"), "page 0 clamps up to 1")
        assertTrue(clampedHigh.first().values.contains("page" to "3"), "page 99 clamps down to the last")
    }

    @Test
    fun `an empty help still renders a page rather than nothing`() {
        val lines = help.page(emptyList(), 1)

        assertEquals(1, help.pageCount(emptyList()))
        assertTrue(lines.size >= 2, "a header and an explanation, not silence")
    }

    @Test
    fun `entries are grouped so a blank line separates root commands`() {
        val entries = entriesFor("sculk.kit", "sculk.kit.admin")
        val lines = help.page(entries, 1)

        assertTrue(lines.any { it.template.isEmpty() }, "expected a separator between /kit and /warp")
    }

    @Test
    fun `an entry without a description uses the shorter template`() {
        val entries = help.entries(listOf(command("bare") { executes { } })) { true }
        val lines = help.page(entries, 1)

        assertTrue(lines.any { it.values.contains("usage" to "/bare") && !it.template.contains("description") })
    }
}
