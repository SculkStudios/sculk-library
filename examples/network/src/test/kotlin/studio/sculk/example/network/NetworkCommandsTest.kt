package studio.sculk.example.network

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.command.CommandDispatcher
import studio.sculk.command.CommandResult
import studio.sculk.testkit.FakeCommandSource
import java.util.UUID

/** The plugin's real command specs and maintenance switch, run without a proxy. */
class NetworkCommandsTest {
    private val steve = FakeCommandSource("Steve")
    private val console = FakeCommandSource.console()
    private val saved = mutableListOf<Boolean>()
    private var saveFails = false
    private val maintenance = Maintenance(enabled = false) { on ->
        if (saveFails) {
            SculkResult.catching("saving config.yml") { error("disk full") }
        } else {
            saved += on
            SculkResult.ok()
        }
    }
    private val network = FakeNetwork()
    private val dispatcher = CommandDispatcher().apply { NetworkCommands(network, maintenance) { Settings() }.all.forEach(::register) }

    @Test
    fun `hub sends a player to the configured hub and says so`() = runTest {
        dispatcher.execute(steve, "hub")
        assertEquals(listOf(checkNotNull(steve.uniqueId) to "lobby"), network.sent)
        assertEquals(listOf("Sending you to lobby."), steve.plainMessages)
    }

    @Test
    fun `hub explains each way a connection can fail`() = runTest {
        network.outcome = Connection.AlreadyThere
        dispatcher.execute(steve, "lobby")
        network.outcome = Connection.UnknownServer
        dispatcher.execute(steve, "hub")
        network.outcome = Connection.Failed("Server is full")
        dispatcher.execute(steve, "hub")
        assertEquals(
            listOf(
                "You are already on lobby.",
                "The hub server lobby is not registered on this proxy.",
                "Could not reach lobby: Server is full",
            ),
            steve.plainMessages,
        )
    }

    @Test
    fun `the console has no server to be sent to`() = runTest {
        assertEquals(CommandResult.PlayersOnly, dispatcher.execute(console, "hub"))
    }

    @Test
    fun `find names a player's server, or says they are offline or still connecting`() = runTest {
        network.players["Alex"] = Location("Alex", "survival")
        network.players["Newbie"] = Location("Newbie", null)
        dispatcher.execute(console, "find alex")
        dispatcher.execute(console, "find Newbie")
        dispatcher.execute(console, "find Herobrine")
        assertEquals(listOf("Alex is on survival.", "Newbie is still connecting.", "Herobrine is not online."), console.plainMessages)
    }

    @Test
    fun `find suggests online players by prefix`() {
        network.players["Alex"] = Location("Alex", "survival")
        network.players["Steve"] = Location("Steve", "lobby")
        assertEquals(listOf("Alex"), dispatcher.suggest(console, "find al"))
    }

    @Test
    fun `turning maintenance on asks first, saves, then disconnects players without the bypass`() = runTest {
        network.withoutBypass = 3
        dispatcher.execute(console, "maintenance on")
        assertFalse(maintenance.enabled)
        dispatcher.execute(console, "maintenance on")
        assertTrue(maintenance.enabled)
        assertEquals(listOf(true), saved)
        assertEquals(
            listOf(
                "Run the command again within 10s to confirm.",
                "Maintenance is on. 3 players without the bypass permission were disconnected.",
            ),
            console.plainMessages,
        )
    }

    @Test
    fun `a failed save leaves maintenance as it was and nobody is disconnected`() = runTest {
        saveFails = true
        network.withoutBypass = 2
        dispatcher.execute(console, "maintenance on")
        val result = dispatcher.execute(console, "maintenance on")
        assertTrue(result is CommandResult.Failed)
        assertFalse(maintenance.enabled)
        assertEquals(0, network.closed)
        assertTrue(console.plainMessages.last().startsWith("Could not save the setting:"))
    }

    @Test
    fun `status and off report the current state and refuse a change that changes nothing`() = runTest {
        dispatcher.execute(console, "maintenance status")
        dispatcher.execute(console, "maintenance off")
        assertEquals(listOf("Maintenance is off.", "Maintenance is already off."), console.plainMessages)
        assertEquals(emptyList<Boolean>(), saved)
    }

    @Test
    fun `while maintenance is on only bypass holders are admitted`() = runTest {
        assertTrue(maintenance.admits(hasBypass = false))
        maintenance.set(true)
        assertFalse(maintenance.admits(hasBypass = false))
        assertTrue(maintenance.admits(hasBypass = true))
    }

    @Test
    fun `players without the maintenance permission do not see the command`() = runTest {
        assertTrue(dispatcher.execute(steve, "maintenance on") is CommandResult.UnknownCommand)
        assertFalse(maintenance.enabled)
    }

    private class FakeNetwork : Network {
        val players = linkedMapOf<String, Location>()
        val sent = mutableListOf<Pair<UUID, String>>()
        var outcome: Connection = Connection.Connected
        var withoutBypass = 0
        var closed = 0

        override fun onlineNames(): Collection<String> = players.keys

        override fun locate(name: String): Location? = players.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override suspend fun connect(player: UUID, server: String): Connection {
            sent += player to server
            return outcome
        }

        override fun closeForMaintenance(): Int {
            closed++
            return withoutBypass
        }
    }
}
