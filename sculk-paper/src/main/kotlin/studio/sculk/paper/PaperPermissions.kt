package studio.sculk.paper

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.event.EventPriority
import studio.sculk.event.listen
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionCheck
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.SculkPermissions
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.permissions.Permission as BukkitPermission
import org.bukkit.permissions.PermissionDefault as BukkitDefault

/**
 * Permissions on Paper, Folia and Canvas, through Bukkit's own registry: descriptors become
 * Bukkit permissions with their defaults and children, so `hasPermission` resolves them natively
 * and LuckPerms or any other provider sees them as it sees plugin.yml permissions.
 *
 * Register from the enabling thread or the global region: Bukkit's registry is not thread-safe.
 * When the result of a check can change for online players, their command trees are resent, each
 * on the player's own thread.
 */
@SculkStable
public class PaperPermissions internal constructor(private val runtime: PaperRuntime) : SculkPermissions {
    private val followed = ConcurrentHashMap.newKeySet<String>()

    override fun register(permissions: Collection<Permission>): SculkHandle {
        val manager = Bukkit.getPluginManager()
        val added = ArrayList<BukkitPermission>(permissions.size)
        try {
            permissions.forEach { permission ->
                check(manager.getPermission(permission.node) == null) {
                    "Permission ${permission.node} is already registered on this server; register each node once."
                }
                val native = permission.toBukkit()
                manager.addPermission(native)
                added += native
            }
        } catch (failure: RuntimeException) {
            added.forEach(::remove)
            throw failure
        }
        updateCommandsForEveryone()
        return runtime.scope.resources.own(
            SculkHandle.once {
                added.forEach(::remove)
                updateCommandsForEveryone()
            },
        )
    }

    /**
     * Resends [player]'s command tree, so every requirement is evaluated again; call it after
     * changing what a `requires { }` condition reads. Runs at once when the caller owns the player,
     * otherwise on the player's next tick.
     */
    public fun updateCommands(player: Player) {
        if (executor.owns(ExecutionContext.Entity(player.ref))) player.updateCommands() else updateCommandsNextTick(player)
    }

    /**
     * Resends a player's command tree whenever a context key [check] reads changes for them, so a
     * command allowed only in some worlds or game modes appears and disappears as the player moves.
     * Paper reports [PermissionContext.WORLD] and [PermissionContext.GAME_MODE]; no other key changes
     * here. Compiled command trees call it for each requirement, so plugins rarely need to.
     */
    public fun follow(check: PermissionCheck) {
        check.contextKeys.filter(followed::add).forEach { key ->
            when (key) {
                PermissionContext.WORLD ->
                    runtime.events.listen<PlayerChangedWorldEvent>(EventPriority.MONITOR) { updateCommandsNextTick(it.player) }

                // Fired before the game mode changes, so the tree is resent a tick later.
                PermissionContext.GAME_MODE ->
                    runtime.events.listen<PlayerGameModeChangeEvent>(EventPriority.MONITOR) { updateCommandsNextTick(it.player) }
            }
        }
    }

    private val executor: SculkExecutor get() = runtime.scope.execution.executor

    // Registering or removing a default changes what online players may run. A stopping runtime
    // resends nothing: the plugin's commands are going away and the server may be shutting down.
    private fun updateCommandsForEveryone() {
        if (runtime.lifecycle.hasReached(LifecyclePhase.STOPPING)) return
        Bukkit.getOnlinePlayers().forEach(::updateCommands)
    }

    private fun updateCommandsNextTick(player: Player) {
        executor.schedule(
            ExecutionContext.Entity(player.ref),
            1,
            object : SculkExecutor.Work {
                override fun run() = player.updateCommands()
            },
        )
    }

    // Paper's removePermission leaves the node among the defaults it hands every permissible, so the
    // default is cleared first; setDefault also recalculates everyone who held it.
    private fun remove(native: BukkitPermission) {
        native.default = BukkitDefault.FALSE
        Bukkit.getPluginManager().removePermission(native)
    }
}

internal fun Permission.toBukkit(): BukkitPermission = BukkitPermission(node, description, default.bukkit, children)

internal val PermissionDefault.bukkit: BukkitDefault
    get() = when (this) {
        PermissionDefault.EVERYONE -> BukkitDefault.TRUE
        PermissionDefault.OPERATORS -> BukkitDefault.OP
        PermissionDefault.NON_OPERATORS -> BukkitDefault.NOT_OP
        PermissionDefault.NOBODY -> BukkitDefault.FALSE
    }
