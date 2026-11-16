package studio.sculk.example.network

import com.google.inject.Inject
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ConnectionRequestBuilder
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerPing
import kotlinx.coroutines.future.await
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.slf4j.Logger
import studio.sculk.command.velocity.commands
import studio.sculk.config.ConfigFile
import studio.sculk.config.SculkConfig
import studio.sculk.event.listen
import studio.sculk.execution.withAsync
import studio.sculk.getOrThrow
import studio.sculk.text.Text
import studio.sculk.velocity.SculkVelocityPlugin
import java.nio.file.Path
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

/**
 * A proxy plugin on Sculk: typed config, permission descriptors, a player-only command that waits
 * for the proxy, a command with confirmation that saves its state, a login gate and a server list
 * change.
 */
class NetworkPlugin @Inject constructor(
    proxy: ProxyServer,
    logger: Logger,
    @DataDirectory dataDirectory: Path,
    container: PluginContainer,
) : SculkVelocityPlugin(proxy, logger, dataDirectory, container) {
    private val text = Text()

    override suspend fun start() {
        val settings = SculkConfig.load<Settings>(dataDirectory.resolve("config.yml")) {
            header = listOf("Network example for Sculk Studio.")
        }.getOrThrow()
        val maintenance = Maintenance(settings.value.maintenance.enabled) { on ->
            withAsync { settings.save(settings.value.withMaintenance(on)) }
        }

        sculk.permissions.register(NetworkPermissions)
        sculk.commands().register(*NetworkCommands(ProxyNetwork(settings), maintenance, settings::value).all.toTypedArray())

        sculk.events.listen<LoginEvent> { event ->
            if (!maintenance.admits(hasBypass(event.player))) {
                event.result = ResultedEvent.ComponentResult.denied(text.render(settings.value.maintenance.message))
            }
        }
        sculk.events.listen<ProxyPingEvent> { event ->
            if (maintenance.enabled) {
                event.ping = event.ping.asBuilder()
                    .description(text.render(settings.value.maintenance.motd))
                    .version(ServerPing.Version(MAINTENANCE_PROTOCOL, "Maintenance"))
                    .build()
            }
        }
    }

    private fun hasBypass(player: Player): Boolean = sculk.permissions.subject(player).hasPermission(NetworkPermissions.bypass)

    private fun Settings.withMaintenance(on: Boolean): Settings = copy(maintenance = maintenance.copy(enabled = on))

    private inner class ProxyNetwork(private val settings: ConfigFile<Settings>) : Network {
        override fun onlineNames(): Collection<String> = proxy.allPlayers.map { it.username }

        override fun locate(name: String): Location? = proxy.getPlayer(name).getOrNull()?.let { player ->
            Location(player.username, player.currentServer.getOrNull()?.serverInfo?.name)
        }

        override suspend fun connect(player: UUID, server: String): Connection {
            val target = proxy.getServer(server).getOrNull() ?: return Connection.UnknownServer
            val online = proxy.getPlayer(player).getOrNull() ?: return Connection.Failed("you are no longer connected")
            val result = online.createConnectionRequest(target).connect().await()
            return when (result.status) {
                ConnectionRequestBuilder.Status.SUCCESS -> Connection.Connected

                ConnectionRequestBuilder.Status.ALREADY_CONNECTED -> Connection.AlreadyThere

                ConnectionRequestBuilder.Status.CONNECTION_IN_PROGRESS -> Connection.InProgress

                ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, ConnectionRequestBuilder.Status.SERVER_DISCONNECTED, null ->
                    Connection.Failed(result.reasonComponent.getOrNull()?.let(PlainTextComponentSerializer.plainText()::serialize))
            }
        }

        override fun closeForMaintenance(): Int {
            val message = text.render(settings.value.maintenance.message)
            val turnedAway = proxy.allPlayers.filterNot(::hasBypass)
            turnedAway.forEach { it.disconnect(message) }
            return turnedAway.size
        }
    }

    private companion object {
        // Any protocol the client does not speak makes it show the version name instead of a ping bar.
        const val MAINTENANCE_PROTOCOL = -1
    }
}
