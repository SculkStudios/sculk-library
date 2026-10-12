package studio.sculk.command

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.testkit.FakeCommandSource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** The examples on the commands page of the documentation, kept compiling and passing here. */
class CommandGuideTest {
    private val balances = mutableMapOf("Steve" to 100.0, "Alex" to 20.0)

    // guide:pay
    private val pay = command("pay") {
        description = "Send coins to another player"
        permission = "economy.pay"
        val target = word("player")
        val amount = double("amount", min = 0.01)
        player {
            val sender = source.name
            val sum = this[amount]
            if ((balances[sender] ?: 0.0) < sum) fail("<danger>You only have <balance> coins.", "balance" to balances[sender])
            balances.merge(sender, -sum, Double::plus)
            balances.merge(this[target], sum, Double::plus)
            reply("<success>Sent <amount> coins to <target>.", "amount" to sum, "target" to this[target])
        }
    }
    // end

    // guide:economy
    private val economy = command("economy", "eco") {
        permission = "economy.admin"
        help()
        literal("give", "add") {
            val target = word("player")
            val amount = double("amount", min = 0.01)
            executes { balances.merge(this[target], this[amount], Double::plus) }
        }
        literal("reset") {
            confirm(10.seconds)
            executes { balances.clear() }
        }
    }
    // end

    // guide:balance
    private val balance = command("balance", "bal") {
        val target = word("player").optional()
        executes {
            val name = this[target] ?: source.name
            reply("<primary><name></primary> has <value><coins></value> coins.", "name" to name, "coins" to (balances[name] ?: 0.0))
        }
    }
    // end

    // guide:daily
    private val daily = command("daily") {
        cooldown(20.hours)
        player { reply("<success>Here are your daily coins.") }
    }
    // end

    @Test
    fun `the guide's commands behave as the page says`() = runTest {
        // guide:dispatch
        val dispatcher = CommandDispatcher()
        dispatcher.register(pay)
        dispatcher.register(balance)
        val steve = FakeCommandSource("Steve", permissions = setOf("economy.pay"))

        dispatcher.execute(steve, "pay Alex 25")
        dispatcher.execute(steve, "balance Alex")

        assertEquals(listOf("Sent 25.0 coins to Alex.", "Alex has 45.0 coins."), steve.plainMessages)
        // end
    }

    @Test
    fun `the admin command lists its forms`() = runTest {
        val dispatcher = CommandDispatcher().apply { register(economy) }
        val console = FakeCommandSource.console()
        dispatcher.execute(console, "eco help")
        assertEquals(
            listOf("/economy can be used as:", "/economy help", "/economy give <player> <amount>", "/economy reset"),
            console.plainMessages,
        )
    }

    @Test
    fun `the daily command runs once per cooldown`() = runTest {
        val dispatcher = CommandDispatcher().apply { register(daily) }
        val steve = FakeCommandSource("Steve")
        dispatcher.execute(steve, "daily")
        dispatcher.execute(steve, "daily")
        assertEquals("Here are your daily coins.", steve.plainMessages.first())
        assertEquals(true, steve.plainMessages.last().startsWith("You can use this again in"))
    }
}
