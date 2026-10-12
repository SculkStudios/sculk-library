package studio.sculk.command

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeCommandSource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CommandDispatcherTest {
    private val dispatcher = CommandDispatcher()
    private val steve = FakeCommandSource("Steve")
    private val console = FakeCommandSource.console()

    private fun register(spec: CommandSpec) = dispatcher.register(spec)

    @Test
    fun `an executor reads its arguments with their declared types`() = runTest {
        var paid: Pair<String, Double>? = null
        register(
            command("pay") {
                val target = word("player")
                val amount = double("amount", min = 0.01)
                executes { paid = this[target] to this[amount] }
            },
        )
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "/pay Alex 12.5"))
        assertEquals("Alex" to 12.5, paid)
    }

    @Test
    fun `an optional trailing argument reads its default when left out`() = runTest {
        val seen = mutableListOf<Int>()
        register(
            command("heal") {
                val amount = integer("amount", min = 1, max = 20).optional(20)
                executes { seen += this[amount] }
            },
        )
        dispatcher.execute(steve, "heal")
        dispatcher.execute(steve, "heal 5")
        assertEquals(listOf(20, 5), seen)
        assertEquals(listOf("/heal [amount]"), dispatcher.usage(dispatcher.commands.single(), steve))
    }

    @Test
    fun `an optional argument without a default reads as null and reports whether it was typed`() = runTest {
        val seen = mutableListOf<String?>()
        register(
            command("balance") {
                val target = word("player").optional()
                executes {
                    seen += this[target]
                    check(isPresent(target) == (this[target] != null))
                }
            },
        )
        dispatcher.execute(steve, "balance")
        dispatcher.execute(steve, "balance Alex")
        assertEquals(listOf(null, "Alex"), seen)
    }

    @Test
    fun `literals branch the command, and aliases reach the same branch`() = runTest {
        val ran = mutableListOf<String>()
        register(
            command("economy", "eco") {
                literal("give", "add") { executes { ran += "give" } }
                literal("take") { executes { ran += "take" } }
            },
        )
        dispatcher.execute(steve, "economy give")
        dispatcher.execute(steve, "eco ADD")
        dispatcher.execute(steve, "eco take")
        assertEquals(listOf("give", "give", "take"), ran)
    }

    @Test
    fun `an invalid argument names the argument, what was expected and what was typed`() = runTest {
        register(command("heal") { integer("amount", min = 1, max = 20).also { executes {} } })
        val result = dispatcher.execute(steve, "heal 50") as CommandResult.InvalidArgument
        assertEquals("amount", result.argument)
        assertEquals(listOf("Invalid <amount>: expected a whole number from 1 to 20, found '50'."), steve.plainMessages)
    }

    @Test
    fun `input that stops early shows how to finish it`() = runTest {
        register(
            command("pay") {
                val target = word("player")
                integer("amount").also { executes { this[target] } }
            },
        )
        assertTrue(dispatcher.execute(steve, "pay Alex") is CommandResult.Incomplete)
        assertEquals(listOf("Incomplete command. Usage: /pay <player> <amount>"), steve.plainMessages)
    }

    @Test
    fun `a sender without the permission is told the command does not exist`() = runTest {
        register(
            command("ban") {
                permission = "mod.ban"
                word("player").also { executes {} }
            },
        )
        assertTrue(dispatcher.execute(steve, "ban Alex") is CommandResult.UnknownCommand)
        assertEquals(emptyList<String>(), dispatcher.suggest(steve, "ba"))
        steve.grant("mod.ban")
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "ban Alex"))
    }

    @Test
    fun `a branch's permission hides only that branch`() = runTest {
        register(
            command("warp") {
                executes {}
                literal("set") {
                    permission = "warp.admin"
                    executes {}
                }
            },
        )
        assertEquals(listOf("/warp"), dispatcher.usage(dispatcher.commands.single(), steve))
        assertTrue(dispatcher.execute(steve, "warp set") is CommandResult.UnknownCommand)
        assertEquals(listOf("/warp", "/warp set"), dispatcher.usage(dispatcher.commands.single(), console))
    }

    @Test
    fun `a players-only executor refuses the console`() = runTest {
        register(command("fly") { player {} })
        assertEquals(CommandResult.PlayersOnly, dispatcher.execute(console, "fly"))
        assertEquals(listOf("Only players can run this command."), console.plainMessages)
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "fly"))
    }

    @Test
    fun `failing a command shows its reason and is not an error`() = runTest {
        register(command("buy") { executes { fail("<danger>You need <price> coins.", "price" to 50) } })
        val result = dispatcher.execute(steve, "buy")
        assertTrue(result is CommandResult.Failed)
        assertEquals(listOf("You need 50 coins."), steve.plainMessages)
    }

    @Test
    fun `an executor that throws gives the sender a generic message and keeps the cause for the log`() = runTest {
        register(command("crash") { executes { error("database is down") } })
        val result = dispatcher.execute(steve, "crash") as CommandResult.Error
        assertEquals("database is down", result.cause.message)
        assertEquals(listOf("Something went wrong running this command; the server log has the details."), steve.plainMessages)
    }

    @Test
    fun `a value in a reply is printed, never obeyed as markup`() = runTest {
        register(command("echo") { greedy("text").also { text -> executes { reply("<primary>You said: <text>", "text" to this[text]) } } })
        dispatcher.execute(steve, "echo <red><click:run_command:'/op Steve'>hi")
        assertEquals(listOf("You said: <red><click:run_command:'/op Steve'>hi"), steve.plainMessages)
    }

    @Test
    fun `a greedy argument takes the rest of the line and a quoted string keeps its spaces`() = runTest {
        val seen = mutableListOf<String>()
        register(
            command("note") {
                string("title").also { title ->
                    greedy("body").also { body ->
                        executes {
                            seen +=
                                this[title] + "|" + this[body]
                        }
                    }
                }
            },
        )
        dispatcher.execute(steve, """note "shopping list" eggs, milk and bread""")
        assertEquals(listOf("shopping list|eggs, milk and bread"), seen)
    }

    @Test
    fun `a cooldown lets each sender run the command once per period and says how long is left`() = runTest {
        val clock = FakeClock()
        var runs = 0
        register(
            command("kit") {
                use(Cooldown(10.minutes, clock))
                executes { runs++ }
            },
        )
        dispatcher.execute(steve, "kit")
        clock.advance(java.time.Duration.ofMinutes(4))
        dispatcher.execute(steve, "kit")
        dispatcher.execute(console, "kit")
        assertEquals(2, runs)
        assertEquals(listOf("You can use this again in 6m."), steve.plainMessages)
        clock.advance(java.time.Duration.ofMinutes(6))
        dispatcher.execute(steve, "kit")
        assertEquals(3, runs)
    }

    @Test
    fun `a failed run does not start the cooldown`() = runTest {
        val clock = FakeClock()
        var attempts = 0
        register(
            command("claim") {
                use(Cooldown(1.hours, clock))
                executes {
                    attempts++
                    if (attempts == 1) fail("Not yet.")
                }
            },
        )
        dispatcher.execute(steve, "claim")
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "claim"))
    }

    @Test
    fun `a confirmed command runs only when repeated within the window`() = runTest {
        val clock = FakeClock()
        var resets = 0
        register(
            command("reset") {
                use(Confirmation(10.seconds, clock))
                executes { resets++ }
            },
        )
        dispatcher.execute(steve, "reset")
        assertEquals(0, resets)
        assertEquals(listOf("Run the command again within 10s to confirm."), steve.plainMessages)
        dispatcher.execute(steve, "reset")
        assertEquals(1, resets)
        dispatcher.execute(steve, "reset")
        clock.advance(java.time.Duration.ofSeconds(11))
        dispatcher.execute(steve, "reset")
        assertEquals(1, resets)
    }

    @Test
    fun `middleware runs outermost first, and inner blocks inherit the outer block's`() = runTest {
        val order = mutableListOf<String>()
        fun named(name: String) = CommandMiddleware { _, proceed ->
            order += name
            proceed()
        }
        register(
            command("admin") {
                use(named("outer"))
                literal("reload") {
                    use(named("inner"))
                    executes { order += "body" }
                }
            },
        )
        dispatcher.execute(console, "admin reload")
        assertEquals(listOf("outer", "inner", "body"), order)
    }

    @Test
    fun `middleware that does not proceed stops the command`() = runTest {
        var ran = false
        register(
            command("maintenance") {
                use { context, _ -> context.reply("<warning>Closed for maintenance.") }
                executes { ran = true }
            },
        )
        dispatcher.execute(steve, "maintenance")
        assertFalse(ran)
        assertEquals(listOf("Closed for maintenance."), steve.plainMessages)
    }

    @Test
    fun `suggestions offer the words and values that fit, for that sender`() {
        register(
            command("gamemode") {
                val mode = choice("mode", mapOf("survival" to 0, "creative" to 1, "spectator" to 3))
                word("player").suggests { partial, _ -> listOf("Steve", "Alex").filter { it.startsWith(partial) } }
                executes { this[mode] }
            },
        )
        assertEquals(listOf("gamemode"), dispatcher.suggest(steve, "game"))
        assertEquals(listOf("spectator", "survival"), dispatcher.suggest(steve, "gamemode s"))
        assertEquals(listOf("Alex"), dispatcher.suggest(steve, "gamemode creative A"))
    }

    @Test
    fun `durations, choices and keys parse the way players write them`() = runTest {
        val seen = mutableListOf<Any>()
        register(
            command("mute") {
                val length = duration("length", max = 30.hours)
                val reason = choice("reason", mapOf("spam" to "Spam", "abuse" to "Abuse"))
                val sound = key("sound")
                executes { seen.add(listOf(this[length], this[reason], this[sound].asString())) }
            },
        )
        dispatcher.execute(console, "mute 1h30m SPAM block.note_block.bell")
        assertEquals(listOf(listOf(90.minutes, "Spam", "minecraft:block.note_block.bell")), seen)
        assertTrue(dispatcher.execute(console, "mute 31h spam x") is CommandResult.InvalidArgument)
    }

    @Test
    fun `generated help lists only the forms the sender may use`() = runTest {
        register(
            command("warp") {
                help()
                word("name").also { executes {} }
                literal("set") {
                    permission = "warp.admin"
                    word("name").also { executes {} }
                }
            },
        )
        dispatcher.execute(steve, "warp help")
        assertEquals(listOf("/warp can be used as:", "/warp help", "/warp <name>"), steve.plainMessages)
        dispatcher.execute(console, "warp help")
        assertEquals(listOf("/warp can be used as:", "/warp help", "/warp <name>", "/warp set <name>"), console.plainMessages)
    }

    @Test
    fun `an unknown command is reported, not ignored`() = runTest {
        assertTrue(dispatcher.execute(steve, "nothing here") is CommandResult.UnknownCommand)
        assertEquals(listOf("Unknown command. Type /help for a list."), steve.plainMessages)
    }
}
