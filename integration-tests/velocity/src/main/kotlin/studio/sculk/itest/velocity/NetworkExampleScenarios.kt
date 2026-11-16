package studio.sculk.itest.velocity

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.network.HandshakeIntent
import com.velocitypowered.api.network.ProtocolState
import com.velocitypowered.api.network.ProtocolVersion
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.server.ServerPing
import kotlinx.coroutines.future.await
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import studio.sculk.execution.delayTicks
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs the network example the way an operator would: its generated config, the replies to its
 * commands, and the server list while maintenance is on. There are no players in CI, so the login
 * gate and `/hub` are covered by the example's own unit tests.
 */
internal class NetworkExampleScenarios(private val plugin: VelocityThreadingPlugin) {
    private val proxy get() = plugin.proxyServer

    suspend fun run() {
        plugin.scenario("network-example-enabled") {
            var waited = 0
            while (!proxy.commandManager.hasCommand("maintenance") && waited++ < 100) delayTicks(1)
            plugin.check(proxy.pluginManager.getPlugin("sculk-network").isPresent && proxy.commandManager.hasCommand("hub")) {
                "plugin present ${proxy.pluginManager.getPlugin("sculk-network").isPresent}, hub ${proxy.commandManager.hasCommand("hub")}"
            }
        }
        plugin.scenario("network-config-generated-with-comments") {
            val text = config().takeIf(Files::isRegularFile)?.let(Files::readString).orEmpty()
            plugin.check("hub: lobby" in text && "# Whether maintenance is on." in text) { "config.yml was\n$text" }
        }
        plugin.scenario("network-hub-is-for-players-only") {
            val replies = run("hub")
            plugin.check(replies == listOf("Only players can run this command.")) { "replies $replies" }
        }
        plugin.scenario("network-find-reports-an-offline-player") {
            val replies = run("find Herobrine")
            plugin.check(replies == listOf("Herobrine is not online.")) { "replies $replies" }
        }
        plugin.scenario("network-maintenance-asks-saves-and-changes-the-server-list") {
            val first = run("maintenance on")
            val second = run("maintenance on")
            val saved = Files.readString(config())
            val ping = proxy.eventManager.fire(ProxyPingEvent(StatusConnection, basePing())).await().ping
            val off = run("maintenance off")
            val restored = proxy.eventManager.fire(ProxyPingEvent(StatusConnection, basePing())).await().ping
            val expected = first == listOf("Run the command again within 10s to confirm.") &&
                second == listOf("Maintenance is on.") &&
                "enabled: true" in saved &&
                ping.version.name == "Maintenance" &&
                plain(ping.descriptionComponent) == "Down for maintenance" &&
                off == listOf("Maintenance is off. Everyone can join again.") &&
                restored.version.name == "Velocity"
            plugin.check(expected) {
                "first $first, second $second, saved enabled ${"enabled: true" in saved}, " +
                    "ping '${ping.version.name}' '${plain(ping.descriptionComponent)}', off $off, restored '${restored.version.name}'"
            }
        }
    }

    private fun config() = plugin.dataDirectoryPath.resolveSibling("sculk-network").resolve("config.yml")

    /** Runs [input] as a source that records its replies and holds every permission. */
    private suspend fun run(input: String): List<String> {
        val replies = CopyOnWriteArrayList<String>()
        val source = object : CommandSource {
            override fun sendMessage(message: Component) {
                replies += plain(message)
            }

            override fun getPermissionValue(permission: String): Tristate = Tristate.TRUE
        }
        proxy.commandManager.executeAsync(source, input).await()
        var waited = 0
        while (replies.isEmpty() && waited++ < 40) delayTicks(1)
        return replies.toList()
    }

    private fun basePing(): ServerPing =
        ServerPing.builder().version(ServerPing.Version(ProtocolVersion.MAXIMUM_VERSION.protocol, "Velocity")).description(Component.text("A Velocity Server")).build()

    private fun plain(component: Component?): String = component?.let(PlainTextComponentSerializer.plainText()::serialize).orEmpty()

    /** A status ping from a client that is not there; only the ping handlers read it. */
    private object StatusConnection : InboundConnection {
        override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress("127.0.0.1", 50_000)

        override fun getVirtualHost(): Optional<InetSocketAddress> = Optional.empty()

        override fun getRawVirtualHost(): Optional<String> = Optional.empty()

        override fun isActive(): Boolean = true

        override fun getProtocolVersion(): ProtocolVersion = ProtocolVersion.MAXIMUM_VERSION

        override fun getProtocolState(): ProtocolState = ProtocolState.STATUS

        override fun getHandshakeIntent(): HandshakeIntent = HandshakeIntent.STATUS

        override fun getSessionId(): UUID = SESSION

        private val SESSION = UUID.randomUUID()
    }
}
