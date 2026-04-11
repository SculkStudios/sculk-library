package gg.sculk.platform.command

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.command.CommandExecutor
import gg.sculk.core.command.CommandNode
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

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
        emptyList(),
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
            // Offer subcommand names
            return node.subcommands
                .filter { it.name.startsWith(args[0], ignoreCase = true) }
                .map { it.name }
        }
        val sub =
            node.subcommands.firstOrNull { it.name.equals(args[0], ignoreCase = true) }
                ?: return emptyList()
        return buildTabCompletions(sub, args.copyOfRange(1, args.size))
    }
}
