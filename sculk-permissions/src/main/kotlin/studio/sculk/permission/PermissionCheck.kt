package studio.sculk.permission

import studio.sculk.annotation.SculkStable

/**
 * Something a [PermissionSubject] passes or fails: a [Permission] descriptor, a descriptor narrowed
 * to a context with [Permission.where], or a raw node from [node]. Immutable and safe to evaluate
 * from any thread the subject may be read on.
 */
@SculkStable
public sealed interface PermissionCheck {
    /** The node the check asks the platform about. */
    public val node: String

    /**
     * The [PermissionContext] keys the outcome depends on besides the node. When one changes for a
     * subject, anything that cached the outcome, such as a client's command tree, is stale.
     */
    public val contextKeys: Set<String>

    public fun test(subject: PermissionSubject): Boolean

    @SculkStable
    public companion object {
        /**
         * A raw node, checked exactly as the platform checks any string: registered defaults and
         * children apply, and an unregistered node follows the platform's own rule.
         */
        public fun node(node: String): PermissionCheck = NodeCheck(node)
    }
}

/**
 * A [permission] held only in some contexts: the subject must hold it, and its context must have
 * each key in [conditions] set to one of the values listed for that key. A subject that reports no
 * value for a key, such as the console for a world, fails. Values compare exactly; platforms report
 * them in lower case.
 */
@SculkStable
public class ContextualPermission internal constructor(public val permission: Permission, conditions: Map<String, Set<String>>) :
    PermissionCheck {
    public val conditions: Map<String, Set<String>> = conditions.toMap()

    override val node: String get() = permission.node

    override val contextKeys: Set<String> get() = conditions.keys

    /** Narrows this check further: [key] must also be one of [values]. Constraining one key twice is a wiring bug and throws. */
    public fun where(key: String, vararg values: String): ContextualPermission {
        require(key.isNotBlank()) { "A context key cannot be blank." }
        require(values.isNotEmpty()) { "${permission.node} where $key needs at least one value." }
        require(key !in conditions) { "${permission.node} already constrains $key; list every allowed value in one where()." }
        return ContextualPermission(permission, conditions + (key to values.toSet()))
    }

    override fun test(subject: PermissionSubject): Boolean {
        val context = subject.permissionContext
        return conditions.all { (key, allowed) -> context[key] in allowed } && subject.hasPermission(permission)
    }

    override fun equals(other: Any?): Boolean = other is ContextualPermission && other.permission == permission &&
        other.conditions == conditions

    override fun hashCode(): Int = 31 * permission.hashCode() + conditions.hashCode()

    override fun toString(): String = "ContextualPermission(${permission.node} where $conditions)"
}

internal class NodeCheck(override val node: String) : PermissionCheck {
    init {
        require(node.isNotBlank()) { "A permission node cannot be blank." }
    }

    override val contextKeys: Set<String> get() = emptySet()

    override fun test(subject: PermissionSubject): Boolean = subject.hasPermission(node)

    override fun equals(other: Any?): Boolean = other is NodeCheck && other.node == node

    override fun hashCode(): Int = node.hashCode()

    override fun toString(): String = "PermissionCheck($node)"
}
