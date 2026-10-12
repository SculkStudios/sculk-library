package studio.sculk.command

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.testkit.FakeCommandSource

class FlagsTest {
    private val dispatcher = CommandDispatcher()
    private val console = FakeCommandSource.console()
    private val seen = mutableListOf<String>()

    private val give = command("give") {
        val player = word("player")
        val item = word("item")
        val silent = flag("silent", short = 's')
        val all = flag("all", short = 'a')
        val amount = flag("amount", Arguments.integer(1, 64), short = 'n')
        executes {
            seen +=
                "${this[player]} ${this[item]} silent=${this[silent]} all=${this[all]} amount=${this[amount]} typed=${isPresent(amount)}"
        }
    }

    init {
        dispatcher.register(give)
    }

    @Test
    fun `a command runs without its flags, which read as their defaults`() = runTest {
        dispatcher.execute(console, "give Steve diamond")
        assertEquals(listOf("Steve diamond silent=false all=false amount=null typed=false"), seen)
    }

    @Test
    fun `flags may be written long, short, combined and in any order`() = runTest {
        dispatcher.execute(console, "give Steve diamond --amount 5 --silent")
        dispatcher.execute(console, "give Steve diamond -sa -n 3")
        assertEquals(
            listOf(
                "Steve diamond silent=true all=false amount=5 typed=true",
                "Steve diamond silent=true all=true amount=3 typed=true",
            ),
            seen,
        )
    }

    @Test
    fun `an unknown, repeated or incomplete flag is invalid and nothing runs`() = runTest {
        listOf("--loud", "--silent --silent", "--amount", "-sn 5", "--amount 99", "loose").forEach { flags ->
            assertTrue(dispatcher.execute(console, "give Steve diamond $flags") is CommandResult.InvalidArgument, flags)
        }
        assertEquals(emptyList<String>(), seen)
    }

    @Test
    fun `usage lists each flag`() {
        assertEquals(listOf("/give <player> <item> [--silent] [--all] [--amount <value>]"), give.usage(console))
    }

    @Test
    fun `suggestions offer the flags not yet written`() {
        assertEquals(
            listOf("--all", "--amount", "--silent"),
            dispatcher.suggest(console, "give Steve diamond --"),
        )
        assertEquals(listOf("--silent --all", "--silent --amount"), dispatcher.suggest(console, "give Steve diamond --silent --a"))
    }

    @Test
    fun `flags come after the arguments and before the executor`() {
        assertThrows<IllegalStateException> {
            command("x") {
                flag("quiet")
                word("name")
            }
        }
        assertThrows<IllegalStateException> {
            command("x") {
                executes {}
                flag("quiet")
            }
        }
        assertThrows<IllegalStateException> {
            command("x") {
                flag("quiet", 'q')
                flag("quick", 'q')
            }
        }
    }
}
