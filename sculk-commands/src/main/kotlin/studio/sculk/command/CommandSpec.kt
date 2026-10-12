package studio.sculk.command

import studio.sculk.annotation.SculkStable
import kotlin.coroutines.cancellation.CancellationException

/**
 * A command as data: a tree of literal and argument nodes, each optionally executable. Built by
 * [command]; compiled by the platform modules into the server's own command tree, and interpreted
 * directly by [CommandDispatcher] for tests. Immutable.
 */
@SculkStable
public class CommandSpec internal constructor(
    public val root: CommandNode.Literal,
    aliases: List<String>,
    public val description: String?,
) {
    public val name: String get() = root.name
    public val aliases: List<String> = aliases.toList()

    /** One line per runnable path that [source] may use: `/pay <player> <amount>`, with optional arguments in brackets. */
    public fun usage(source: CommandSource): List<String> = buildList {
        fun visit(node: CommandNode, path: String) {
            if (!node.requirement.test(source)) return
            val here = when (node) {
                is CommandNode.Literal -> "$path ${node.name}"
                is CommandNode.Argument<*> -> if (node.handle.isOptional) "$path [${node.name}]" else "$path <${node.name}>"
            }
            val executor = node.executor
            val delegatesToOptional = node.children.any { child ->
                child is CommandNode.Argument<*> && child.handle.isOptional && child.executor?.body === executor?.body
            }
            if (executor != null && !delegatesToOptional) add("/" + here.trim())
            node.children.forEach { visit(it, here) }
        }
        visit(root, "")
    }

    override fun toString(): String = "CommandSpec(/$name)"
}

/** One node of a command tree. */
@SculkStable
public sealed class CommandNode {
    /** The literal word, or the argument's name. */
    public abstract val name: String
    public abstract val children: List<CommandNode>

    /** Runs when input ends at this node; null when this node only leads further. */
    public abstract val executor: CommandExecutor?

    /** Who may see and use this node and everything below it. */
    public abstract val requirement: Requirement

    /** A fixed word. Matching ignores case. */
    @SculkStable
    public class Literal internal constructor(
        override val name: String,
        children: List<CommandNode>,
        override val executor: CommandExecutor?,
        override val requirement: Requirement,
    ) : CommandNode() {
        override val children: List<CommandNode> = children.toList()

        override fun toString(): String = "Literal($name)"
    }

    /** A typed value, read with [type]; [suggestions] replaces the type's own completions when set. */
    @SculkStable
    public class Argument<T : Any> internal constructor(
        public val handle: ArgumentHandle<*>,
        public val type: ArgumentType<T>,
        public val suggestions: SuggestionProvider?,
        children: List<CommandNode>,
        override val executor: CommandExecutor?,
        override val requirement: Requirement,
    ) : CommandNode() {
        override val name: String get() = handle.name
        override val children: List<CommandNode> = children.toList()

        override fun toString(): String = "Argument($name: ${type.expected})"
    }
}

/**
 * A typed reference to an argument, read in an executor with `this[handle]`. An optional
 * argument's handle reads its default, or null, when the sender left it out.
 */
@SculkStable
public class ArgumentHandle<out T> internal constructor(public val name: String, internal val declaration: ArgumentDeclaration) {
    /** True when the sender may leave this argument out. */
    public val isOptional: Boolean get() = declaration.optional

    override fun toString(): String = "<$name>"
}

internal class ArgumentDeclaration(val name: String, val type: ArgumentType<*>) {
    var optional = false
    var default: Any? = null
    var suggestions: SuggestionProvider? = null
}

/** What runs a command: middleware wrapped around the body, outermost first. */
@SculkStable
public class CommandExecutor internal constructor(
    middleware: List<CommandMiddleware>,
    public val requiresPlayer: Boolean,
    internal val body: suspend CommandContext.() -> Unit,
) {
    public val middleware: List<CommandMiddleware> = middleware.toList()

    /**
     * Runs the middleware and the body for [context] in the calling coroutine and reports the
     * outcome. [CommandContext.fail] and exceptions become results; cancellation propagates.
     */
    public suspend fun run(context: CommandContext): CommandResult {
        if (requiresPlayer && !context.source.kind.isPlayer) return CommandResult.PlayersOnly
        return try {
            proceed(context, 0)
            CommandResult.Success
        } catch (failed: CommandFailedException) {
            CommandResult.Failed(failed.reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CommandResult.Error(error)
        }
    }

    private suspend fun proceed(context: CommandContext, index: Int) {
        if (index == middleware.size) {
            body(context)
            return
        }
        middleware[index].intercept(context) { proceed(context, index + 1) }
    }
}

/** Completions computed per sender, for arguments whose values depend on the server. */
@SculkStable
public fun interface SuggestionProvider {
    public fun suggest(partial: String, source: CommandSource): List<String>
}

/** A permission and an extra condition; a sender who fails either never sees the node. */
@SculkStable
public class Requirement internal constructor(public val permission: String?, private val condition: ((CommandSource) -> Boolean)?) {
    public fun test(source: CommandSource): Boolean =
        (permission == null || source.hasPermission(permission)) && (condition == null || condition(source))

    @SculkStable
    public companion object {
        public val NONE: Requirement = Requirement(null, null)
    }
}
