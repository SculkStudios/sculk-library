package gg.sculk.core.command

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.command.argument.ArgumentParser

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

    public var playerExecutor: (CommandContext.() -> Unit)? = null
    public var consoleExecutor: (CommandContext.() -> Unit)? = null
    public var anyExecutor: (CommandContext.() -> Unit)? = null

    public fun findSubcommand(name: String): CommandNode? = subcommands.firstOrNull { it.name.equals(name, ignoreCase = true) }
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
