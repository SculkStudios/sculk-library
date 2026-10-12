package studio.sculk.itest.velocity

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.permission.Tristate
import kotlinx.coroutines.future.await
import studio.sculk.command.CommandSpec
import studio.sculk.command.command
import studio.sculk.execution.delayTicks
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup
import java.util.concurrent.ConcurrentHashMap

object ProxyPermissions : PermissionGroup("sculk.vtest") {
    val use = permission("use", "Use the proxy test commands", PermissionDefault.EVERYONE)
    val manage = permission("manage", "Manage the proxy test commands")
    val secret = permission("secret", "Held by nobody unless granted", PermissionDefault.NOBODY)
    val guest = permission("guest", "Held by every player", PermissionDefault.NON_OPERATORS)
    val all = permission("*", "Every restricted proxy test permission", children = listOf(manage, secret))
}

/**
 * A command source that is neither a player nor the console, whose provider answers only the
 * nodes the scenario sets, as LuckPerms answers on a proxy, and leaves the rest undefined.
 */
internal class ProxySubjectSource : CommandSource {
    val values = ConcurrentHashMap<String, Boolean>()

    override fun getPermissionValue(permission: String): Tristate = Tristate.fromNullableBoolean(values[permission])
}

/** Descriptor resolution on a real proxy, and command requirements that follow it through Velocity's dispatcher. */
internal class ProxyPermissionScenarios(private val plugin: VelocityThreadingPlugin) {
    val specs: List<CommandSpec> = listOf(
        command("vtest-perm-use") {
            permission(ProxyPermissions.use)
            executes { plugin.recorded += "use" }
        },
        command("vtest-perm-manage") {
            permission(ProxyPermissions.manage)
            executes { plugin.recorded += "manage" }
        },
        command("vtest-perm-raw") {
            permission = "sculk.vtest.use"
            executes { plugin.recorded += "raw" }
        },
    )

    private val permissions get() = plugin.sculk.permissions

    fun register() {
        permissions.register(ProxyPermissions)
    }

    suspend fun run() {
        plugin.scenario("descriptor-defaults-apply-on-the-proxy") { defaults() }
        plugin.scenario("provider-values-and-parents-beat-defaults-on-the-proxy") { providerValues() }
        plugin.scenario("console-holds-everything-on-the-proxy") { console() }
        plugin.scenario("command-requirements-follow-the-descriptor-on-the-proxy") { requirements() }
        plugin.scenario("proxy-registration-ends-with-its-handle") { handleRemoval() }
    }

    private val descriptors = listOf(ProxyPermissions.use, ProxyPermissions.manage, ProxyPermissions.secret, ProxyPermissions.guest)

    private fun defaults(): String {
        val subject = permissions.subject(ProxySubjectSource())
        val held = descriptors.map { subject.hasPermission(it) }
        val raw = descriptors.map { subject.hasPermission(it.node) }
        val unregistered = subject.hasPermission("sculk.vtest.unknown")
        return plugin.check(held == listOf(true, false, false, true) && raw == held && !unregistered) {
            "descriptors $held, raw nodes $raw, unregistered node $unregistered"
        }
    }

    private fun providerValues(): String {
        val source = ProxySubjectSource()
        val subject = permissions.subject(source)
        source.values["sculk.vtest.*"] = true
        val granted = listOf(ProxyPermissions.manage, ProxyPermissions.secret).map { subject.hasPermission(it) }
        source.values["sculk.vtest.*"] = false
        val denied = listOf(ProxyPermissions.manage, ProxyPermissions.secret).map { subject.hasPermission(it) }
        source.values["sculk.vtest.use"] = false
        val useDenied = subject.hasPermission(ProxyPermissions.use)
        return plugin.check(granted == listOf(true, true) && denied == listOf(false, false) && !useDenied) {
            "parent granted $granted, parent denied $denied, use denied by provider held $useDenied"
        }
    }

    private fun console(): String {
        val subject = permissions.subject(plugin.proxyServer.consoleCommandSource)
        val held = descriptors.map { subject.hasPermission(it) }
        return plugin.check(held.all { it }) { "console holds $held" }
    }

    private suspend fun requirements(): String {
        val source = ProxySubjectSource()
        val outcome = listOf("vtest-perm-use", "vtest-perm-manage", "vtest-perm-raw").map { run(source, it) }
        source.values["sculk.vtest.manage"] = true
        val granted = run(source, "vtest-perm-manage")
        return plugin.check(outcome == listOf("use", null, "raw") && granted == "manage") {
            "use, manage, raw node ran $outcome; manage once granted ran '$granted'"
        }
    }

    private fun handleRemoval(): String {
        val temporary = Permission("sculk.vtest.temporary", default = PermissionDefault.EVERYONE)
        val subject = permissions.subject(ProxySubjectSource())
        val handle = permissions.register(temporary)
        val whileRegistered = subject.hasPermission(temporary.node)
        handle.close()
        val afterClose = subject.hasPermission(temporary.node)
        val again = permissions.register(temporary)
        again.close()
        return plugin.check(whileRegistered && !afterClose) { "raw node while registered $whileRegistered, after close $afterClose" }
    }

    private suspend fun run(source: CommandSource, input: String): String? {
        plugin.recorded.clear()
        plugin.proxyServer.commandManager.executeAsync(source, input).await()
        delayTicks(4)
        return plugin.recorded.poll().also { plugin.recorded.clear() }
    }
}
