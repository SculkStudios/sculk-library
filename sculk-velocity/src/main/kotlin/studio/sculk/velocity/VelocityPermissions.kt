package studio.sculk.velocity

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionResolver
import studio.sculk.permission.PermissionSubject
import studio.sculk.permission.SculkPermissions

/**
 * Permissions on Velocity. The proxy has no registry of defaults: a provider such as LuckPerms
 * answers a node or leaves it undefined, and without one every player's node is undefined while the
 * console holds everything. This registry fills the gap for this plugin's checks: where the
 * provider leaves a registered node undefined, children of a parent it sets and then the
 * descriptor's default decide, as on Bukkit. The console is the only operator, so
 * [OPERATORS][studio.sculk.permission.PermissionDefault.OPERATORS] holds for no player and
 * [NON_OPERATORS][studio.sculk.permission.PermissionDefault.NON_OPERATORS] for every player.
 *
 * Registrations are this plugin's own; other plugins and checks through Velocity's own API do not
 * see them. Thread-safe.
 */
@SculkStable
public class VelocityPermissions internal constructor(private val runtime: VelocityRuntime) : SculkPermissions {
    private val lock = Any()

    @Volatile
    internal var resolver: PermissionResolver = PermissionResolver(emptyList())
        private set

    override fun register(permissions: Collection<Permission>): SculkHandle {
        val batch = permissions.toList()
        synchronized(lock) { resolver = PermissionResolver(resolver.permissions + batch) }
        return runtime.scope.resources.own(
            SculkHandle.once {
                synchronized(lock) { resolver = PermissionResolver(resolver.permissions.filterNot { it in batch }) }
            },
        )
    }

    /** [source] as a [PermissionSubject] whose checks apply this plugin's registered descriptors. */
    public fun subject(source: CommandSource): PermissionSubject = VelocityPermissionSubject(source, this)
}

/**
 * A Velocity source as a [PermissionSubject]: Velocity's own answer first, then the descriptors
 * registered in [registry], or only each descriptor's own default without one. A player's context
 * is the backend server it is connected to.
 */
@SculkStable
public class VelocityPermissionSubject(public val source: CommandSource, private val registry: VelocityPermissions? = null) :
    PermissionSubject {
    private val resolver: PermissionResolver get() = registry?.resolver ?: UNREGISTERED

    override fun hasPermission(permission: String): Boolean =
        resolver.find(permission)?.let { hasPermission(it) } ?: source.hasPermission(permission)

    override fun hasPermission(permission: Permission): Boolean =
        resolver.resolve(permission, operator = source is ConsoleCommandSource) { node ->
            when (source.getPermissionValue(node)) {
                Tristate.TRUE -> true
                Tristate.FALSE -> false
                Tristate.UNDEFINED -> null
            }
        }

    override val permissionContext: PermissionContext
        get() = PermissionContext.of(
            PermissionContext.SERVER to (source as? Player)?.currentServer?.map { it.serverInfo.name }?.orElse(null),
        )

    override fun toString(): String = "VelocityPermissionSubject($source)"

    private companion object {
        val UNREGISTERED = PermissionResolver(emptyList())
    }
}
