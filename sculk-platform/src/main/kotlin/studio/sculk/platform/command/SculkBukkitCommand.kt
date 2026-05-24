package studio.sculk.platform.command

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.core.command.CommandExecutor
import studio.sculk.core.command.CommandNode

/**
 * Adapts a Sculk [CommandNode] tree to Bukkit's [Command] API.
 *
 * Registered into Paper's server command map by [SculkCommandBridge].
 */
@SculkInternal
public class SculkBukkitCommand(
    private val node: CommandNode,
    pluginName: String,
) : Command(
        node.name,
        node.description,
        "/${node.name}",
        node.aliases.toList(),
    ) {
    init {
        permission = node.permission
        permissionMessage = "<red>You don't have permission to use this command."
        setLabel("$pluginName:${node.name}")
    }

    override fun execute(
        sender: CommandSender,
        commandLabel: String,
        args: Array<String>,
    ): Boolean {
        CommandExecutor.dispatch(node, sender, commandLabel, args)
        return true
    }

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<String>,
    ): List<String> = buildTabCompletions(node, args)

    private fun buildTabCompletions(
        node: CommandNode,
        args: Array<String>,
    ): List<String> {
        if (args.isEmpty()) return emptyList()
        if (args.size == 1) {
            val subSuggestions =
                node.subcommands
                    .filter { it.name.startsWith(args[0], ignoreCase = true) }
                    .map { it.name }
            val argSuggestions =
                node.arguments
                    .firstOrNull()
                    ?.parser
                    ?.suggest(args[0]) ?: emptyList()
            return (subSuggestions + argSuggestions).distinct()
        }
        // Recurse into matched subcommand
        val sub = node.subcommands.firstOrNull { it.name.equals(args[0], ignoreCase = true) }
        if (sub != null) return buildTabCompletions(sub, args.copyOfRange(1, args.size))
        // No subcommand — completing an argument; args.last() is the partial token
        val argIdx = args.size - 1
        return node.arguments
            .getOrNull(argIdx)
            ?.parser
            ?.suggest(args.last()) ?: emptyList()
    }
}
