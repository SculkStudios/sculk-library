package studio.sculk.permission

import studio.sculk.annotation.SculkStable

/**
 * A permission declared once, as a value: its node, what it is for, who holds it when nothing sets
 * it, and the nodes it passes on. Declare descriptors as properties of a [PermissionGroup] or as
 * top-level vals, register them with the platform once, and check them wherever they apply.
 *
 * A node is lower-case words of letters, digits, `-` and `_` joined by dots, optionally ending in
 * `.*` for a parent that only gathers children; anything else throws, since it is a typo the
 * platform would silently accept. [children] map nodes to the value a holder of this permission
 * gets for them; a subject denied this permission gets the opposite, as on Bukkit. Immutable.
 */
@SculkStable
public class Permission(
    node: String,
    public val description: String? = null,
    public val default: PermissionDefault = PermissionDefault.OPERATORS,
    children: Map<String, Boolean> = emptyMap(),
) : PermissionCheck {
    override val node: String = node.also(::requireNode)

    public val children: Map<String, Boolean> = children.toMap().also { declared ->
        declared.keys.forEach(::requireNode)
        require(this.node !in declared) { "Permission $node lists itself as a child." }
    }

    override val contextKeys: Set<String> get() = emptySet()

    override fun test(subject: PermissionSubject): Boolean = subject.hasPermission(this)

    /**
     * This permission, held only where the subject's [PermissionContext] has [key] set to one of
     * [values], such as `fly.where(PermissionContext.WORLD, "minecraft:the_nether")`.
     */
    public fun where(key: String, vararg values: String): ContextualPermission = ContextualPermission(this, emptyMap()).where(key, *values)

    override fun equals(other: Any?): Boolean = other is Permission && other.node == node && other.description == description &&
        other.default == default && other.children == children

    override fun hashCode(): Int = listOf(node, description, default, children).hashCode()

    override fun toString(): String = "Permission($node, $default)"

    @SculkStable
    public companion object {
        private val NODE = Regex("[a-z0-9_-]+(\\.[a-z0-9_-]+)*(\\.\\*)?")

        /** True when [node] is a node a descriptor accepts. */
        public fun isValidNode(node: String): Boolean = NODE.matches(node)

        internal fun requireNode(node: String) {
            require(isValidNode(node)) {
                "Permission node '$node' must be lower-case words of letters, digits, '-' or '_' joined by '.', optionally ending in '.*'."
            }
        }
    }
}

/** Who holds a permission when nothing sets it for them. */
@SculkStable
public enum class PermissionDefault {
    EVERYONE,
    OPERATORS,
    NON_OPERATORS,
    NOBODY,
    ;

    /** Whether a subject that is, or is not, an [operator] holds a permission with this default. */
    public fun appliesTo(operator: Boolean): Boolean = when (this) {
        EVERYONE -> true
        OPERATORS -> operator
        NON_OPERATORS -> !operator
        NOBODY -> false
    }
}
