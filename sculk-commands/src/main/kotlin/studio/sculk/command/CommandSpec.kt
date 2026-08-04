package studio.sculk.command

import studio.sculk.annotation.SculkStable
import studio.sculk.command.argument.ArgumentParser
import studio.sculk.command.argument.BooleanParser
import studio.sculk.command.argument.BoundedDoubleParser
import studio.sculk.command.argument.BoundedIntParser
import studio.sculk.command.argument.BoundedLongParser
import studio.sculk.command.argument.ChoiceParser
import studio.sculk.command.argument.DurationParser
import studio.sculk.command.argument.EnumParser
import studio.sculk.command.argument.GreedyStringParser
import studio.sculk.command.argument.MaterialParser
import studio.sculk.command.argument.PlayerNameParser
import studio.sculk.command.argument.PlayerParser
import studio.sculk.command.argument.StringParser
import studio.sculk.command.argument.UuidParser
import studio.sculk.command.argument.WorldParser
import java.time.Duration

/**
 * One argument slot.
 *
 * [optional] renders as `[name]` rather than `<name>` and makes the node executable without it.
 */
@SculkStable
public data class Argument<T>(public val name: String, public val parser: ArgumentParser<T>, public val optional: Boolean = false) {
    /** `<player>` or `[reason]`. */
    public val usage: String get() = if (optional) "[$name]" else "<$name>"
}

/**
 * A command tree as data — no Brigadier, no Bukkit types in its shape.
 *
 * The whole point of the split: a spec can be built, flattened, rendered as usage, and filtered by
 * permission with no server running, so the parts most likely to be wrong are the parts that are
 * cheapest to test. Only [studio.sculk.command.brigadier.BrigadierAdapter] needs a live server, and
 * it is the only file in the repo that imports Brigadier.
 *
 * ```kotlin
 * command("kit") {
 *     permission = "sculk.kit"
 *     sub("give") {
 *         player("target")
 *         choice("kit", options = { kits.names() })
 *         executes { … }
 *     }
 * }
 * ```
 */
@SculkStable
public data class CommandSpec(
    public val name: String,
    public val aliases: List<String> = emptyList(),
    public val description: String = "",
    public val permission: String? = null,
    public val arguments: List<Argument<*>> = emptyList(),
    public val children: List<CommandSpec> = emptyList(),
    public val cooldown: Duration? = null,
    public val middleware: List<suspend (CommandContext) -> Boolean> = emptyList(),
    public val playerExecutor: (suspend CommandContext.() -> Unit)? = null,
    public val consoleExecutor: (suspend CommandContext.() -> Unit)? = null,
    public val anyExecutor: (suspend CommandContext.() -> Unit)? = null,
) {
    /** True when this node does something itself, as opposed to only holding children. */
    public val executable: Boolean
        get() = playerExecutor != null || consoleExecutor != null || anyExecutor != null

    /** The usage line, e.g. `/kit give <target> <kit>`. */
    public fun usage(parentPath: String = ""): String {
        val path = if (parentPath.isEmpty()) name else "$parentPath $name"
        val args = arguments.joinToString(" ") { it.usage }
        return if (args.isEmpty()) "/$path" else "/$path $args"
    }

    /** Every executable node in this tree, paired with its full path. */
    public fun flatten(parentPath: String = ""): List<Pair<String, CommandSpec>> {
        val path = if (parentPath.isEmpty()) name else "$parentPath $name"
        val here = if (executable) listOf(path to this) else emptyList()
        return here + children.flatMap { it.flatten(path) }
    }

    /** The usage line for a full path such as `kit give`, or null if there is no such node. */
    public fun usageAt(fullPath: String): String? {
        val parts = fullPath.trim().split(" ").filter { it.isNotEmpty() }
        if (parts.isEmpty() || !matches(parts.first())) return null

        var node = this
        var parent = ""
        for (part in parts.drop(1)) {
            parent = if (parent.isEmpty()) node.name else "$parent ${node.name}"
            node = node.child(part) ?: return null
        }
        return node.usage(parent)
    }

    /** Finds a direct child by name or alias, case-insensitively. */
    public fun child(name: String): CommandSpec? = children.firstOrNull { it.matches(name) }

    internal fun matches(token: String): Boolean =
        name.equals(token, ignoreCase = true) || aliases.any { it.equals(token, ignoreCase = true) }
}

/** Builds a [CommandSpec]. */
@SculkStable
public class CommandBuilder internal constructor(private val name: String) {
    public var permission: String? = null
    public var description: String = ""
    public var aliases: List<String> = emptyList()

    private val arguments = mutableListOf<Argument<*>>()
    private val children = mutableListOf<CommandSpec>()
    private val middleware = mutableListOf<suspend (CommandContext) -> Boolean>()
    private var cooldown: Duration? = null
    private var playerExecutor: (suspend CommandContext.() -> Unit)? = null
    private var consoleExecutor: (suspend CommandContext.() -> Unit)? = null
    private var anyExecutor: (suspend CommandContext.() -> Unit)? = null

    /** Runs for players only. May be declared alongside [console]; each sender reaches its own. */
    public fun player(block: suspend CommandContext.() -> Unit) {
        playerExecutor = block
    }

    /** Runs for the console and command blocks only. */
    public fun console(block: suspend CommandContext.() -> Unit) {
        consoleExecutor = block
    }

    /** Runs for any sender. Takes precedence over [player] and [console]. */
    public fun executes(block: suspend CommandContext.() -> Unit) {
        anyExecutor = block
    }

    /** Rate-limits this node per sender. */
    public fun cooldown(duration: Duration) {
        cooldown = duration
    }

    /**
     * Runs before the executor; returning false aborts dispatch.
     *
     * The filter is responsible for telling the sender why, since only it knows.
     */
    public fun middleware(block: suspend (CommandContext) -> Boolean) {
        middleware += block
    }

    public fun sub(name: String, block: CommandBuilder.() -> Unit) {
        children += CommandBuilder(name).apply(block).build()
    }

    /** Adds an already-built child, for composing specs across files. */
    public fun child(spec: CommandSpec) {
        children += spec
    }

    public fun string(name: String, optional: Boolean = false): Unit = add(name, StringParser, optional)

    public fun int(name: String, optional: Boolean = false, min: Int? = null, max: Int? = null): Unit =
        add(name, BoundedIntParser(min, max), optional)

    public fun long(name: String, optional: Boolean = false, min: Long? = null, max: Long? = null): Unit =
        add(name, BoundedLongParser(min, max), optional)

    public fun double(name: String, optional: Boolean = false, min: Double? = null, max: Double? = null): Unit =
        add(name, BoundedDoubleParser(min, max), optional)

    public fun boolean(name: String, optional: Boolean = false): Unit = add(name, BooleanParser, optional)

    public fun player(name: String, optional: Boolean = false): Unit = add(name, PlayerParser, optional)

    /**
     * A player name that need not be online, read back with `argument<String>(name)`.
     *
     * Use this rather than [player] for anything a moderator does to somebody who has already
     * left -- bans, lookups, history -- and rather than [string], which silently has no
     * completions at all.
     */
    public fun playerName(name: String, optional: Boolean = false): Unit = add(name, PlayerNameParser, optional)

    public fun uuid(name: String, optional: Boolean = false): Unit = add(name, UuidParser, optional)

    public fun world(name: String, optional: Boolean = false): Unit = add(name, WorldParser, optional)

    public fun material(name: String, optional: Boolean = false): Unit = add(name, MaterialParser, optional)

    public fun duration(name: String, optional: Boolean = false): Unit = add(name, DurationParser, optional)

    public fun <E : Enum<E>> enum(name: String, type: Class<E>, optional: Boolean = false): Unit = add(name, EnumParser(type), optional)

    /** Consumes the rest of the line. Only valid as the final argument. */
    public fun greedy(name: String): Unit = add(name, GreedyStringParser, optional = false)

    /**
     * One of a set of values.
     *
     * [options] is a lambda, never a captured list: a snapshot taken when the command was
     * registered stops matching the moment a config reload changes the set, and tab-completion
     * quietly goes stale for the rest of the server's uptime.
     */
    public fun choice(name: String, optional: Boolean = false, typeName: String = name, options: () -> Collection<String>): Unit =
        add(name, ChoiceParser(typeName, options), optional)

    /** Adds an argument backed by a custom parser. */
    public fun <T : Any> argument(name: String, parser: ArgumentParser<T>, optional: Boolean = false): Unit = add(name, parser, optional)

    private fun <T> add(name: String, parser: ArgumentParser<T>, optional: Boolean) {
        require(arguments.none { it.name == name }) { "Duplicate argument name '$name' on /$this.name." }
        require(arguments.lastOrNull()?.optional != true || optional) {
            "Required argument '$name' cannot follow an optional one."
        }
        arguments += Argument(name, parser, optional)
    }

    internal fun build(): CommandSpec = CommandSpec(
        name = name,
        aliases = aliases,
        description = description,
        permission = permission,
        arguments = arguments.toList(),
        children = children.toList(),
        cooldown = cooldown,
        middleware = middleware.toList(),
        playerExecutor = playerExecutor,
        consoleExecutor = consoleExecutor,
        anyExecutor = anyExecutor,
    )
}

/** Declares a command. The result is data; registering it is a separate step. */
@SculkStable
public fun command(name: String, block: CommandBuilder.() -> Unit): CommandSpec = CommandBuilder(name).apply(block).build()
