package studio.sculk.command

import studio.sculk.annotation.SculkInternal
import studio.sculk.command.argument.ArgumentParser

/**
 * A single node in the command tree.
 *
 * Each node has a [name], optional [permission], optional [description],
 * a list of [arguments], child [subcommands], and at most one executor per
 * sender type: [playerExecutor], [consoleExecutor], or [anyExecutor].
 */
@SculkInternal
public class CommandNode(
    public val name: String,
) {
    public var permission: String? = null
    public var description: String = ""
    public val aliases: MutableList<String> = mutableListOf()
    public val subcommands: MutableList<CommandNode> = mutableListOf()
    public val arguments: MutableList<ArgumentDefinition> = mutableListOf()
    public var cooldown: CooldownDefinition? = null

    /**
     * Pre-execution filters run in order before the executor. Returning `false` aborts
     * dispatch (the filter is responsible for messaging the sender). Useful for logging,
     * rate-limiting, or contextual permission checks.
     */
    public val middleware: MutableList<suspend (CommandContext) -> Boolean> = mutableListOf()

    public var playerExecutor: (suspend CommandContext.() -> Unit)? = null
    public var consoleExecutor: (suspend CommandContext.() -> Unit)? = null
    public var anyExecutor: (suspend CommandContext.() -> Unit)? = null

    public fun findSubcommand(name: String): CommandNode? =
        subcommands.firstOrNull { subcommand ->
            subcommand.name.equals(name, ignoreCase = true) ||
                subcommand.aliases.any { it.equals(name, ignoreCase = true) }
        }
}

/**
 * Metadata for a single argument slot on a [CommandNode].
 */
@SculkInternal
public data class ArgumentDefinition(
    val name: String,
    val parser: ArgumentParser<*>,
    val optional: Boolean,
)

@SculkInternal
public data class CooldownDefinition(
    val key: String,
    val durationMillis: Long,
)
