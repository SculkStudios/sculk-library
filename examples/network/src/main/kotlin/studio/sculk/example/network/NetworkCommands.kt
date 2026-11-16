package studio.sculk.example.network

import studio.sculk.command.CommandSpec
import studio.sculk.command.command
import studio.sculk.failureOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** Where an online player is: their current name, and their server once they have one. */
data class Location(val player: String, val server: String?)

/** What happened when a player was sent to a server. */
sealed interface Connection {
    data object Connected : Connection

    data object AlreadyThere : Connection

    data object InProgress : Connection

    data object UnknownServer : Connection

    /** The server refused or could not be reached; [reason] is what it said, as plain text. */
    data class Failed(val reason: String?) : Connection
}

/** The proxy as the commands see it. The plugin backs it with Velocity; tests back it with a map. */
interface Network {
    fun onlineNames(): Collection<String>

    fun locate(name: String): Location?

    suspend fun connect(player: UUID, server: String): Connection

    /** Disconnects every player without the bypass permission and returns how many there were. */
    fun closeForMaintenance(): Int
}

/**
 * The plugin's commands, built from the network, the maintenance switch and the settings and
 * nothing from Velocity, so the same specs run on the proxy and in unit tests.
 */
class NetworkCommands(private val network: Network, private val maintenance: Maintenance, private val settings: () -> Settings) {
    val hub: CommandSpec = command("hub", "lobby") {
        description = "Go to the hub"
        permission(NetworkPermissions.hub)
        player {
            val id = source.uniqueId ?: fail("<danger>Only players can run this command.")
            val hub = settings().hub
            when (val outcome = network.connect(id, hub)) {
                Connection.Connected -> reply("<success>Sending you to <server>.", "server" to hub)

                Connection.AlreadyThere -> fail("<muted>You are already on <server>.", "server" to hub)

                Connection.InProgress -> fail("<muted>You are already being sent to a server.")

                Connection.UnknownServer -> fail("<danger>The hub server <server> is not registered on this proxy.", "server" to hub)

                is Connection.Failed -> fail(
                    "<danger>Could not reach <server>: <reason>",
                    "server" to hub,
                    "reason" to (outcome.reason ?: "no reason given"),
                )
            }
        }
    }

    val find: CommandSpec = command("find") {
        description = "See which server a player is on"
        permission(NetworkPermissions.find)
        val name = word("player").suggests { partial, _ -> network.onlineNames().filter { it.startsWith(partial, ignoreCase = true) } }
        executes {
            val location = network.locate(this[name]) ?: fail("<danger><name> is not online.", "name" to this[name])
            val server = location.server ?: fail("<muted><name> is still connecting.", "name" to location.player)
            reply("<primary><name></primary> is on <value><server></value>.", "name" to location.player, "server" to server)
        }
    }

    val maintenanceCommand: CommandSpec = command("maintenance") {
        description = "Turn maintenance mode on or off"
        permission(NetworkPermissions.maintenance)
        help()
        literal("on") {
            confirm(10.seconds)
            executes {
                if (maintenance.enabled) fail("<muted>Maintenance is already on.")
                maintenance.set(true).failureOrNull()?.let { fail("<danger>Could not save the setting: <reason>", "reason" to it.message) }
                when (val removed = network.closeForMaintenance()) {
                    0 -> reply("<warning>Maintenance is on.")

                    1 -> reply("<warning>Maintenance is on. 1 player without the bypass permission was disconnected.")

                    else -> reply(
                        "<warning>Maintenance is on. <count> players without the bypass permission were disconnected.",
                        "count" to removed,
                    )
                }
            }
        }
        literal("off") {
            executes {
                if (!maintenance.enabled) fail("<muted>Maintenance is already off.")
                maintenance.set(false).failureOrNull()?.let { fail("<danger>Could not save the setting: <reason>", "reason" to it.message) }
                reply("<success>Maintenance is off. Everyone can join again.")
            }
        }
        literal("status") {
            executes {
                if (maintenance.enabled) reply("<warning>Maintenance is on.") else reply("<success>Maintenance is off.")
            }
        }
    }

    val all: List<CommandSpec> get() = listOf(hub, find, maintenanceCommand)
}
