package studio.sculk.permission

import studio.sculk.annotation.SculkStable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A plugin's permissions under one prefix, declared as the properties of an object:
 *
 * ```kotlin
 * object ShopPermissions : PermissionGroup("shop") {
 *     val buy = permission("buy", "Buy from the shop", PermissionDefault.EVERYONE)
 *     val sell = permission("sell", "Sell to the shop", PermissionDefault.EVERYONE)
 *     val admin = permission("admin", "Change prices and stock")
 *     val all = permission("*", "Every shop permission", children = listOf(buy, sell, admin))
 * }
 * ```
 *
 * An object initialises all its properties the first time it is used, so registering the group
 * with [SculkPermissions.register] registers every descriptor in it, in declaration order.
 */
@SculkStable
public abstract class PermissionGroup(public val prefix: String) {
    private val declared = CopyOnWriteArrayList<Permission>()

    init {
        require(Permission.isValidNode(prefix) && !prefix.endsWith("*")) {
            "Permission prefix '$prefix' must be lower-case words of letters, digits, '-' or '_' joined by '.'."
        }
    }

    /** Every descriptor declared in this group, in declaration order. */
    public val permissions: List<Permission> get() = declared.toList()

    /**
     * Declares `prefix.name`. A holder of it also holds each of [children]. Declaring one name twice
     * is a wiring bug and throws.
     */
    protected fun permission(
        name: String,
        description: String? = null,
        default: PermissionDefault = PermissionDefault.OPERATORS,
        children: Collection<Permission> = emptyList(),
    ): Permission = include(Permission("$prefix.$name", description, default, children.associate { it.node to true }))

    /** Adds a descriptor built with the full [Permission] constructor, such as one that denies a child. */
    protected fun include(permission: Permission): Permission {
        synchronized(declared) {
            check(declared.none { it.node == permission.node }) { "${permission.node} is declared twice in the $prefix group." }
            declared += permission
        }
        return permission
    }

    override fun toString(): String = "PermissionGroup($prefix, ${declared.size} permissions)"
}
