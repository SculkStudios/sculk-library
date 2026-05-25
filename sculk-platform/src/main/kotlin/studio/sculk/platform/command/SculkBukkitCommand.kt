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
    ): List<String> = buildTabCompletions(node, sender, args)

    private fun buildTabCompletions(
        node: CommandNode,
        sender: CommandSender,
        args: Array<String>,
    ): List<String> {
        if (args.isEmpty()) return emptyList()
        if (args.size == 1) {
            val subSuggestions =
                node.subcommands
                    .filter { it.canUse(sender) }
                    .flatMap { subcommand -> listOf(subcommand.name) + subcommand.aliases }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            val argSuggestions =
                node.arguments
                    .firstOrNull()
                    ?.parser
                    ?.suggest(args[0]) ?: emptyList()
            return (subSuggestions + argSuggestions).distinct()
        }

        val sub = node.findSubcommand(args[0])
        if (sub != null && sub.canUse(sender)) return buildTabCompletions(sub, sender, args.copyOfRange(1, args.size))

        val argIdx = args.size - 1
        return node.arguments
            .getOrNull(argIdx)
            ?.parser
            ?.suggest(args.last()) ?: emptyList()
    }

    private fun CommandNode.canUse(sender: CommandSender): Boolean = permission?.let(sender::hasPermission) ?: true
}
