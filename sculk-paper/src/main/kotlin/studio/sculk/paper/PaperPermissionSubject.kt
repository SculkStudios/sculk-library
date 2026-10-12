package studio.sculk.paper

import org.bukkit.command.BlockCommandSender
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.HumanEntity
import studio.sculk.annotation.SculkStable
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionCheck
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionSubject

/**
 * A Bukkit sender as a [PermissionSubject]. Checks are [sender]'s, through Bukkit's
 * `hasPermission`, so LuckPerms and other providers decide as usual. The context is [entity]'s:
 * its world's key and, for a player, its game mode, both in lower case; a command block reports
 * its world, the console nothing. It is read when a check needs it, on the calling thread.
 */
@SculkStable
public class PaperPermissionSubject(public val sender: CommandSender, public val entity: Entity? = sender as? Entity) :
    PermissionSubject {
    override fun hasPermission(permission: String): Boolean = sender.hasPermission(permission)

    /** The sender's own value where a provider or attachment sets one; otherwise the descriptor's default, registered or not. */
    override fun hasPermission(permission: Permission): Boolean =
        if (sender.isPermissionSet(permission.node)) sender.hasPermission(permission.node) else permission.default.appliesTo(sender.isOp)

    override val permissionContext: PermissionContext
        get() {
            val world = entity?.world ?: (sender as? BlockCommandSender)?.block?.world
            return PermissionContext.of(
                PermissionContext.WORLD to world?.key?.asString(),
                PermissionContext.GAME_MODE to (entity as? HumanEntity)?.gameMode?.name?.lowercase(),
            )
        }

    override fun toString(): String = "PaperPermissionSubject(${sender.name})"
}

/** This sender as a [PermissionSubject], with its world and, for a player, its game mode as context. */
@SculkStable
public val CommandSender.permissionSubject: PermissionSubject get() = PaperPermissionSubject(this)

/** Whether this sender passes [check]: a descriptor, whose default applies where nothing sets it, or a contextual check. */
@SculkStable
public fun CommandSender.hasPermission(check: PermissionCheck): Boolean = check.test(permissionSubject)
