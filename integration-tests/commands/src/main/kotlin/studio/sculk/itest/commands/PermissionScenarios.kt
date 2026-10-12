package studio.sculk.itest.commands

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.permissions.PermissionDefault as BukkitDefault
import studio.sculk.command.CommandSpec
import studio.sculk.command.command
import studio.sculk.command.paper.PaperCommandSource
import studio.sculk.paper.PaperPermissionSubject
import studio.sculk.paper.Sculk
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup

object ItestPermissions : PermissionGroup("sculk.itest") {
    val use = permission("use", "Use the test commands", PermissionDefault.EVERYONE)
    val manage = permission("manage", "Manage the test commands")
    val secret = permission("secret", "Held by nobody unless granted", PermissionDefault.NOBODY)
    val guest = permission("guest", "Held by everyone but operators", PermissionDefault.NON_OPERATORS)
    val all = permission("*", "Every restricted test permission", PermissionDefault.NOBODY, listOf(manage, secret))
}

/**
 * Registration with Bukkit's real registry, resolution by Bukkit's own permissibles, and command
 * requirements that follow the descriptors, on every server the suite runs on.
 */
internal class PermissionScenarios(private val plugin: CommandsPlugin) {
    val specs: List<CommandSpec> = listOf(
        command("itest-perm-use") {
            permission(ItestPermissions.use)
            executes { plugin.record("use") }
        },
        command("itest-perm-manage") {
            permission(ItestPermissions.manage)
            executes { plugin.record("manage") }
        },
        command("itest-perm-secret") {
            permission(ItestPermissions.secret)
            executes { plugin.record("secret") }
        },
        command("itest-perm-raw") {
            permission = "sculk.itest.use"
            executes { plugin.record("raw") }
        },
        command("itest-perm-creative") {
            permission(ItestPermissions.use.where(PermissionContext.GAME_MODE, "creative"))
            executes { plugin.record("creative") }
        },
    )

    private val server get() = plugin.server
    private val world get() = server.worlds.first()

    fun register() {
        plugin.sculk.permissions.register(ItestPermissions)
    }

    suspend fun run() {
        plugin.scenario("permissions-registered-with-defaults-and-children") { registered() }
        plugin.scenario("permission-defaults-follow-operator-status") { defaults() }
        plugin.scenario("granted-parent-passes-its-children") { children() }
        plugin.scenario("console-holds-operator-defaults-but-not-nobody") { console() }
        plugin.scenario("command-requirements-follow-the-descriptor") { requirements() }
        plugin.scenario("contextual-requirements-follow-the-game-mode") { contextual() }
        plugin.scenario("registration-ends-with-its-handle") { handleRemoval() }
        plugin.scenario("permissions-removed-when-the-runtime-closes") { runtimeRemoval() }
        plugin.scenario("duplicate-registration-is-refused-whole") { duplicates() }
    }

    private fun registered(): String {
        val manager = server.pluginManager
        val expected = mapOf(
            "sculk.itest.use" to BukkitDefault.TRUE,
            "sculk.itest.manage" to BukkitDefault.OP,
            "sculk.itest.secret" to BukkitDefault.FALSE,
            "sculk.itest.guest" to BukkitDefault.NOT_OP,
            "sculk.itest.*" to BukkitDefault.FALSE,
        )
        val wrong = expected.filter { (node, default) -> manager.getPermission(node)?.default != default }
        val all = manager.getPermission("sculk.itest.*")
        return plugin.check(
            wrong.isEmpty() && all?.children == mapOf("sculk.itest.manage" to true, "sculk.itest.secret" to true) &&
                manager.getPermission("sculk.itest.use")?.description == "Use the test commands",
        ) { "wrong defaults ${wrong.keys}, children ${all?.children}" }
    }

    private fun defaults(): String {
        val expected = mapOf(
            true to listOf(true, true, false, false),
            false to listOf(true, false, false, true),
        )
        val descriptors = listOf(ItestPermissions.use, ItestPermissions.manage, ItestPermissions.secret, ItestPermissions.guest)
        val wrong = expected.mapNotNull { (operator, values) ->
            val player = TestPlayer(operator, world).player
            val native = descriptors.map { player.hasPermission(it.node) }
            val sculk = descriptors.map { PaperPermissionSubject(player).hasPermission(it) }
            if (native == values && sculk == values) null else "operator=$operator: native $native, descriptor $sculk"
        }
        return plugin.check(wrong.isEmpty()) { wrong.joinToString("; ") }
    }

    private fun children(): String {
        val player = TestPlayer(operator = false, world).player
        val attachment = player.addAttachment(plugin, "sculk.itest.*", true)
        val granted = listOf(ItestPermissions.manage, ItestPermissions.secret).map { PaperPermissionSubject(player).hasPermission(it) }
        player.removeAttachment(attachment)
        val after = PaperPermissionSubject(player).hasPermission(ItestPermissions.secret)
        return plugin.check(granted == listOf(true, true) && !after) { "with the parent $granted, without it secret=$after" }
    }

    private suspend fun console(): String {
        val console = server.consoleSender
        val checks = listOf(ItestPermissions.use, ItestPermissions.manage, ItestPermissions.secret, ItestPermissions.guest)
            .map { PaperPermissionSubject(console).hasPermission(it) }
        val manage = plugin.runOnPaper("itest-perm-manage")
        val secret = plugin.runOnPaper("itest-perm-secret")
        return plugin.check(checks == listOf(true, true, false, false) && manage == "manage" && secret == null) {
            "console holds $checks, manage ran '$manage', secret ran '$secret'"
        }
    }

    private fun requirements(): String {
        val player = TestPlayer(operator = false, world).player
        val operator = TestPlayer(operator = true, world).player
        fun allows(spec: String, sender: Player): Boolean =
            specs.first { it.name == spec }.root.requirement.test(PaperCommandSource(stackOf(sender)))
        val outcome = listOf(
            allows("itest-perm-use", player),
            allows("itest-perm-manage", player),
            allows("itest-perm-manage", operator),
            allows("itest-perm-raw", player),
            allows("itest-perm-secret", operator),
        )
        return plugin.check(outcome == listOf(true, false, true, true, false)) {
            "use, manage (player), manage (operator), raw node, secret (operator): $outcome"
        }
    }

    private fun contextual(): String {
        val tester = TestPlayer(operator = false, world)
        val spec = specs.first { it.name == "itest-perm-creative" }
        val inSurvival = spec.root.requirement.test(PaperCommandSource(stackOf(tester.player)))
        tester.gameMode = GameMode.CREATIVE
        val context = PaperPermissionSubject(tester.player).permissionContext
        val inCreative = spec.root.requirement.test(PaperCommandSource(stackOf(tester.player)))
        val followed = listOf(PlayerGameModeChangeEvent.getHandlerList(), PlayerChangedWorldEvent.getHandlerList())
            .map { handlers -> handlers.registeredListeners.any { it.plugin == plugin } }
        val expectedContext = PermissionContext.of(
            PermissionContext.WORLD to world.key.asString(),
            PermissionContext.GAME_MODE to "creative",
        )
        return plugin.check(!inSurvival && inCreative && context == expectedContext && followed == listOf(true, false)) {
            "survival $inSurvival, creative $inCreative, context $context, listening for game mode and world $followed"
        }
    }

    private fun handleRemoval(): String {
        val temporary = Permission("sculk.itest.temporary", default = PermissionDefault.EVERYONE)
        val player = TestPlayer(operator = false, world).player
        val handle = plugin.sculk.permissions.register(temporary)
        val whileRegistered = player.hasPermission(temporary.node)
        handle.close()
        val afterClose = player.hasPermission(temporary.node)
        val reRegistered = plugin.sculk.permissions.register(temporary)
        val again = server.pluginManager.getPermission(temporary.node) != null
        reRegistered.close()
        return plugin.check(whileRegistered && !afterClose && again && server.pluginManager.getPermission(temporary.node) == null) {
            "while registered $whileRegistered, after close $afterClose, registered again $again"
        }
    }

    private fun runtimeRemoval(): String {
        val closing = Permission("sculk.itest.closing", default = PermissionDefault.EVERYONE)
        val other = Sculk.attach(plugin)
        other.permissions.register(closing)
        val registered = server.pluginManager.getPermission(closing.node) != null
        other.close()
        val manager = server.pluginManager
        val gone = manager.getPermission(closing.node) == null
        val noDefault = manager.getDefaultPermissions(false).none { it.name == closing.node } &&
            manager.getDefaultPermissions(true).none { it.name == closing.node }
        val held = TestPlayer(operator = false, world).player.hasPermission(closing.node)
        return plugin.check(registered && gone && noDefault && !held) {
            "registered $registered, removed $gone, no default left $noDefault, still held $held"
        }
    }

    private fun duplicates(): String {
        val fresh = Permission("sculk.itest.fresh")
        val refused = try {
            plugin.sculk.permissions.register(fresh, ItestPermissions.use)
            false
        } catch (_: IllegalStateException) {
            true
        }
        val leftover = server.pluginManager.getPermission(fresh.node) != null
        return plugin.check(refused && !leftover) { "refused $refused, left ${fresh.node} registered $leftover" }
    }
}
