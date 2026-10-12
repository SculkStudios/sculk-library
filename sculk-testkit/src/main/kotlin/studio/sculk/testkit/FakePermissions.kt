package studio.sculk.testkit

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionResolver
import studio.sculk.permission.PermissionSubject
import studio.sculk.permission.SculkPermissions
import java.util.concurrent.ConcurrentHashMap

/**
 * A [SculkPermissions] for tests: keeps what was registered, refuses a node registered twice as the
 * platforms do, and resolves defaults and children for its [subject]s with Bukkit's rules.
 * Thread-safe.
 */
@SculkStable
public class FakePermissions : SculkPermissions {
    private val lock = Any()

    @Volatile
    private var resolver = PermissionResolver(emptyList())

    /** Every descriptor registered now, in registration order. */
    public val registered: List<Permission> get() = resolver.permissions

    override fun register(permissions: Collection<Permission>): SculkHandle {
        val batch = permissions.toList()
        synchronized(lock) { resolver = PermissionResolver(resolver.permissions + batch) }
        return SculkHandle.once {
            synchronized(lock) { resolver = PermissionResolver(resolver.permissions.filterNot { it in batch }) }
        }
    }

    /** A subject whose checks apply the descriptors registered here at the time of each check. */
    public fun subject(
        operator: Boolean = false,
        permissions: Map<String, Boolean> = emptyMap(),
        context: PermissionContext = PermissionContext.EMPTY,
    ): FakePermissionSubject = FakePermissionSubject(operator, permissions, context) { resolver }
}

/**
 * A [PermissionSubject] for tests. A node it sets in [permissions] wins; a registered descriptor
 * then follows its parents and defaults; any other node is held by operators only, as on Bukkit.
 * Thread-safe.
 */
@SculkStable
public class FakePermissionSubject internal constructor(
    public val operator: Boolean,
    permissions: Map<String, Boolean>,
    context: PermissionContext,
    private val resolver: () -> PermissionResolver,
) : PermissionSubject {
    /** A subject with no registered descriptors: a descriptor's own default and the nodes it sets decide. */
    public constructor(
        operator: Boolean = false,
        permissions: Map<String, Boolean> = emptyMap(),
        context: PermissionContext = PermissionContext.EMPTY,
    ) : this(operator, permissions, context, { EMPTY })

    private val values = ConcurrentHashMap(permissions)

    @Volatile
    override var permissionContext: PermissionContext = context

    /** Sets [node] to [value], as a permission provider would. */
    public fun set(node: String, value: Boolean) {
        values[node] = value
    }

    /** Clears the value for [node], so defaults and parents decide again. */
    public fun unset(node: String) {
        values.remove(node)
    }

    override fun hasPermission(permission: String): Boolean {
        val registered = resolver().find(permission) ?: return values[permission] ?: operator
        return hasPermission(registered)
    }

    override fun hasPermission(permission: Permission): Boolean = resolver().resolve(permission, operator) { values[it] }

    override fun toString(): String = "FakePermissionSubject(operator=$operator, $values, $permissionContext)"

    private companion object {
        val EMPTY = PermissionResolver(emptyList())
    }
}
