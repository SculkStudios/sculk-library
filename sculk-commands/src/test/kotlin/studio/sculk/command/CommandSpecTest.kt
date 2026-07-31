package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSpecTest {
    private val kit = command("kit") {
        description = "Manage kits."
        aliases = listOf("kits")
        sub("give") {
            description = "Gives a kit."
            player("target")
            string("kit")
            executes { }
        }
        sub("list") {
            description = "Lists kits."
            int("page", optional = true)
            executes { }
        }
    }

    @Test
    fun `usage renders required and optional arguments differently`() {
        assertEquals("/kit give <target> <kit>", kit.child("give")!!.usage("kit"))
        assertEquals("/kit list [page]", kit.child("list")!!.usage("kit"))
    }

    @Test
    fun `flatten yields every executable leaf with its full path`() {
        assertEquals(listOf("kit give", "kit list"), kit.flatten().map { it.first })
    }

    @Test
    fun `a node that only holds children is not itself executable`() {
        assertTrue(!kit.executable)
    }

    @Test
    fun `usageAt resolves a full path`() {
        assertEquals("/kit give <target> <kit>", kit.usageAt("kit give"))
        assertEquals("/kit list [page]", kit.usageAt("kit give".replace("give", "list")))
    }

    @Test
    fun `usageAt returns null for a path that does not exist`() {
        assertNull(kit.usageAt("kit nope"))
        assertNull(kit.usageAt("warp"))
    }

    @Test
    fun `a child is found by alias as well as by name`() {
        val spec = command("root") {
            sub("teleport") {
                aliases = listOf("tp")
                executes { }
            }
        }

        assertEquals("teleport", spec.child("tp")?.name)
        assertEquals("teleport", spec.child("TELEPORT")?.name)
    }

    @Test
    fun `a required argument cannot follow an optional one`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            command("bad") {
                string("first", optional = true)
                string("second")
            }
        }

        assertTrue(failure.message!!.contains("cannot follow an optional"))
    }

    @Test
    fun `a duplicate argument name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            command("bad") {
                string("name")
                int("name")
            }
        }
    }

    @Test
    fun `a suggestion lambda reflects a value added after the spec was built`() {
        val kits = mutableListOf("starter")
        val spec = command("kit") {
            choice("kit", options = { kits })
            executes { }
        }
        val parser = spec.arguments.single().parser

        assertEquals(listOf("starter"), parser.suggest(""))

        kits += "pvp"

        // A snapshot captured at registration would still report one entry here, and tab-complete
        // would stay stale until the next restart.
        assertEquals(listOf("starter", "pvp"), parser.suggest(""))
    }

    @Test
    fun `a node may declare both a player and a console executor`() {
        val spec = command("seen") {
            player { }
            console { }
        }

        assertTrue(spec.playerExecutor != null, "the player executor must survive")
        assertTrue(spec.consoleExecutor != null, "and so must the console one")
    }
}
