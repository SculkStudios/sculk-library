package studio.sculk.permission

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable

/**
 * A runtime's permission registry. Each platform implements it over its native system: Paper
 * registers Bukkit permissions, Velocity keeps the descriptors itself. Thread-safe.
 */
@SculkStable
public interface SculkPermissions {
    /**
     * Declares [permissions] to the platform, so their defaults and children apply to every check
     * of their nodes, including raw string checks and other plugins' checks where the platform
     * shares one registry. They stay registered until the returned handle or the plugin scope
     * closes. A node that is already registered is a wiring bug and throws [IllegalStateException];
     * nothing from the call stays registered then.
     */
    public fun register(permissions: Collection<Permission>): SculkHandle

    /** Registers every descriptor of [group], in declaration order. */
    public fun register(group: PermissionGroup): SculkHandle = register(group.permissions)

    public fun register(vararg permissions: Permission): SculkHandle = register(permissions.asList())
}
