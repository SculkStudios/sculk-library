package studio.sculk.permission

import studio.sculk.annotation.SculkStable

/**
 * Whoever a permission is checked for, as every platform describes it: a player, the console, a
 * command sender. Platform modules adapt their own types; every check goes through the platform's
 * native permission system, so LuckPerms and other providers apply unchanged.
 */
@SculkStable
public interface PermissionSubject {
    /**
     * Whether the platform grants [permission], a raw node: what the subject's provider sets wins,
     * then registered defaults and children; an unregistered node follows the platform's own rule.
     */
    public fun hasPermission(permission: String): Boolean

    /**
     * Whether the subject holds [permission]: its own value for the node where one is set,
     * otherwise the descriptor's [default][Permission.default], registered or not. This default
     * implementation asks [hasPermission] for the node, which suits a subject that sets every node
     * explicitly; platform subjects override it so the descriptor's default applies.
     */
    public fun hasPermission(permission: Permission): Boolean = hasPermission(permission.node)

    /** Where the subject is, for [ContextualPermission]: empty unless the platform knows. */
    public val permissionContext: PermissionContext get() = PermissionContext.EMPTY
}

/** Whether this subject passes [check]: a descriptor, a contextual check or a raw node. */
@SculkStable
public fun PermissionSubject.hasPermission(check: PermissionCheck): Boolean = check.test(this)

/**
 * Facts about where a subject is, read by [ContextualPermission]: the world, the game mode, the
 * backend server. A key is absent when it does not apply, such as a world for the console.
 * Immutable.
 */
@SculkStable
public class PermissionContext(values: Map<String, String>) {
    private val values: Map<String, String> = values.toMap()

    init {
        require(this.values.keys.none(String::isBlank)) { "A context key cannot be blank." }
    }

    public val keys: Set<String> get() = values.keys

    /** The value for [key], or null when the subject has none. */
    public operator fun get(key: String): String? = values[key]

    override fun equals(other: Any?): Boolean = other is PermissionContext && other.values == values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "PermissionContext($values)"

    @SculkStable
    public companion object {
        /** The world's key, such as `minecraft:overworld`. Reported on Paper, Folia and Canvas. */
        public const val WORLD: String = "world"

        /** `survival`, `creative`, `adventure` or `spectator`. Reported for players on Paper, Folia and Canvas. */
        public const val GAME_MODE: String = "gamemode"

        /** The name of the backend server a player is connected to. Reported on Velocity. */
        public const val SERVER: String = "server"

        public val EMPTY: PermissionContext = PermissionContext(emptyMap())

        /** A context of the [pairs] whose value is not null. */
        public fun of(vararg pairs: Pair<String, String?>): PermissionContext =
            PermissionContext(pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())
    }
}
