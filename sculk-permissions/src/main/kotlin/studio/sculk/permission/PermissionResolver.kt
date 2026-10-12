package studio.sculk.permission

import studio.sculk.annotation.SculkStable

/**
 * Bukkit's rules for defaults and children over a fixed set of descriptors, for platforms with no
 * registry of their own, such as Velocity, and for fakes. For a node, in order:
 *
 * 1. the value the subject sets for it, through its permission provider;
 * 2. the value it gets from a parent the subject sets: the child's value when the parent is
 *    granted, the opposite when it is denied;
 * 3. its default, or the grant of a parent the subject holds by default.
 *
 * A node nothing decides is not held. Cycles among children are cut rather than followed.
 * Immutable and thread-safe; a registry replaces its resolver when its descriptors change.
 */
@SculkStable
public class PermissionResolver(permissions: Collection<Permission>) {
    public val permissions: List<Permission> = permissions.toList()

    private val byNode: Map<String, Permission> = buildMap {
        this@PermissionResolver.permissions.forEach { permission ->
            check(putIfAbsent(permission.node, permission) == null) { "${permission.node} is registered twice." }
        }
    }

    private val parents: Map<String, List<Permission>> = this.permissions
        .flatMap { parent -> parent.children.keys.map { child -> child to parent } }
        .groupBy({ it.first }, { it.second })

    /** The registered descriptor for [node], or null. */
    public fun find(node: String): Permission? = byNode[node]

    /**
     * Whether a subject holds [permission]. [explicit] answers the subject's own value for a node,
     * null where it sets none; [operator] decides which defaults apply. A descriptor that is not
     * registered still has its own default; only registered parents pass values down.
     */
    public fun resolve(permission: Permission, operator: Boolean, explicit: (String) -> Boolean?): Boolean =
        explicitValue(permission.node, explicit, HashSet()) ?: byDefault(permission.node, permission.default, operator, HashSet())

    private fun explicitValue(node: String, explicit: (String) -> Boolean?, visited: MutableSet<String>): Boolean? {
        explicit(node)?.let { return it }
        if (!visited.add(node)) return null
        for (parent in parents[node].orEmpty()) {
            val granted = explicitValue(parent.node, explicit, visited) ?: continue
            val value = parent.children.getValue(node)
            return if (granted) value else !value
        }
        return null
    }

    private fun byDefault(node: String, own: PermissionDefault, operator: Boolean, visited: MutableSet<String>): Boolean {
        if (own.appliesTo(operator)) return true
        if (!visited.add(node)) return false
        return parents[node].orEmpty().any { parent ->
            parent.children.getValue(node) && byDefault(parent.node, parent.default, operator, visited)
        }
    }

    override fun toString(): String = "PermissionResolver(${permissions.size} permissions)"
}
