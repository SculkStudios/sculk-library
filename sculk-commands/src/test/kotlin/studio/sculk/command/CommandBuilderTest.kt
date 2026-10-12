package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandBuilderTest {
    @Test
    fun `the same word twice at one position is refused when declared`() {
        val failure = assertThrows<IllegalStateException> {
            command("warp") {
                literal("set") {}
                literal("SET".lowercase()) {}
            }
        }
        assertEquals("'set' is declared twice after warp.", failure.message)
    }

    @Test
    fun `a literal alias that clashes with another literal is refused`() {
        assertThrows<IllegalStateException> {
            command("eco") {
                literal("give", "add") {}
                literal("add") {}
            }
        }
    }

    @Test
    fun `nothing may follow a greedy argument`() {
        val failure = assertThrows<IllegalStateException> {
            command("say") {
                greedy("message")
                word("extra")
            }
        }
        assertEquals("Nothing can follow the greedy argument <message>.", failure.message)
    }

    @Test
    fun `a required argument after an optional one is refused`() {
        val failure = assertThrows<IllegalStateException> {
            command("give") {
                word("player").optional()
                integer("amount")
                executes {}
            }
        }
        assertEquals("<amount> is required but follows the optional <player>; only the last arguments can be optional.", failure.message)
    }

    @Test
    fun `two arguments with one name on a path are refused`() {
        assertThrows<IllegalStateException> {
            command("copy") {
                word("name")
                literal("to") { word("name") }
            }
        }
    }

    @Test
    fun `a node has one executor`() {
        assertThrows<IllegalStateException> {
            command("spawn") {
                executes {}
                executes {}
            }
        }
    }

    @Test
    fun `command words are lower case without spaces`() {
        assertThrows<IllegalArgumentException> { command("Spawn") {} }
        assertThrows<IllegalArgumentException> { command("set home") {} }
        assertThrows<IllegalArgumentException> { command("x") { word("Player") } }
    }

    @Test
    fun `a range with min above max is refused`() {
        assertThrows<IllegalArgumentException> { command("x") { integer("n", min = 5, max = 1) } }
    }
}
