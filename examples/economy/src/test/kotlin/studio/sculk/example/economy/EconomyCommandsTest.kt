package studio.sculk.example.economy

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.command.CommandDispatcher
import studio.sculk.command.CommandResult
import studio.sculk.testkit.FakeCommandSource
import java.util.UUID

/** The plugin's real command specs, run without a server. */
class EconomyCommandsTest {
    private val steve = FakeCommandSource("Steve")
    private val alex = FakeCommandSource("Alex")
    private val admin = FakeCommandSource.console()
    private val players = object : Players {
        private val known = listOf(steve, alex).associate { it.name to Holder(checkNotNull(it.uniqueId), it.name) }

        override fun find(name: String): Holder? = known.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun name(id: UUID): String? = known.values.firstOrNull { it.id == id }?.name

        override fun names(): Collection<String> = known.keys
    }
    private val ledger = Ledger(startingBalance = 100.0)
    private val dispatcher = CommandDispatcher().apply { EconomyCommands(ledger, players) { Settings() }.all.forEach(::register) }

    @Test
    fun `a payment moves money between two players and tells the sender`() = runTest {
        dispatcher.execute(steve, "pay Alex 25")
        assertEquals(75.0, ledger.balance(checkNotNull(steve.uniqueId)))
        assertEquals(125.0, ledger.balance(checkNotNull(alex.uniqueId)))
        assertEquals(listOf("Sent 25.00 coins to Alex."), steve.plainMessages)
    }

    @Test
    fun `a player cannot pay more than they have or pay themselves`() = runTest {
        assertEquals(true, dispatcher.execute(steve, "pay Alex 500") is CommandResult.Failed)
        assertEquals(true, dispatcher.execute(steve, "pay Steve 5") is CommandResult.Failed)
        assertEquals(listOf("You only have 100.00 coins.", "You cannot pay yourself."), steve.plainMessages)
        assertEquals(100.0, ledger.balance(checkNotNull(steve.uniqueId)))
    }

    @Test
    fun `the console pays nobody, since it has no account`() = runTest {
        assertEquals(CommandResult.PlayersOnly, dispatcher.execute(admin, "pay Alex 5"))
    }

    @Test
    fun `admins give quietly with a flag, take, and see the richest players`() = runTest {
        dispatcher.execute(admin, "eco give Alex 50 --silent")
        dispatcher.execute(admin, "eco take Steve 40")
        dispatcher.execute(admin, "eco top -n 2")
        assertEquals(
            listOf("Took 40.00 coins from Steve.", "1. Alex 150.00 coins", "2. Steve 60.00 coins"),
            admin.plainMessages,
        )
    }

    @Test
    fun `players without the admin permission never see the admin command`() = runTest {
        assertEquals(true, dispatcher.execute(steve, "eco give Steve 1000") is CommandResult.UnknownCommand)
    }

    @Test
    fun `resetting every balance asks for confirmation first`() = runTest {
        ledger.deposit(checkNotNull(alex.uniqueId), 10.0)
        dispatcher.execute(admin, "eco reset")
        assertEquals(110.0, ledger.balance(checkNotNull(alex.uniqueId)))
        dispatcher.execute(admin, "eco reset")
        assertEquals(100.0, ledger.balance(checkNotNull(alex.uniqueId)))
    }

    @Test
    fun `a balance check names the player, or the sender when none is given`() = runTest {
        dispatcher.execute(steve, "balance")
        dispatcher.execute(steve, "bal Alex")
        dispatcher.execute(steve, "balance Herobrine")
        assertEquals(
            listOf("Steve has 100.00 coins.", "Alex has 100.00 coins.", "Nobody called Herobrine has played here."),
            steve.plainMessages,
        )
    }

    @Test
    fun `balances survive a save and a reload`() {
        ledger.deposit(checkNotNull(steve.uniqueId), 5.5)
        val restored = SavedBalances.of(ledger).toLedger(startingBalance = 100.0)
        assertEquals(105.5, restored.balance(checkNotNull(steve.uniqueId)))
    }
}
