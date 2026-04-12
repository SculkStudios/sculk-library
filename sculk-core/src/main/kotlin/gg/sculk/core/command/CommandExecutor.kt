package gg.sculk.core.command

import gg.sculk.core.adventure.reply
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.command.argument.GreedyStringParser
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Walks the command tree and dispatches execution for a given [CommandNode].
 *
 * This is internal infrastructure. Plugin code only interacts with the DSL.
 */
@SculkInternal
public object CommandExecutor {
    private const val NO_PERMISSION = "<red>You don't have permission to use this command."
    private const val PLAYER_ONLY = "<red>This command can only be used by players."
    private const val CONSOLE_ONLY = "<red>This command can only be used from the console."
    private const val UNKNOWN_SUB = "<red>Unknown subcommand. Use <yellow>/{label} help</yellow> for a list."
    private const val BAD_USAGE = "<red>Usage: <yellow>/{usage}"

    /**
     * Dispatches execution starting from [root] with the given [sender] and [args].
     */
    public fun dispatch(
        root: CommandNode,
        sender: CommandSender,
        label: String,
        args: Array<String>,
    ) {
        // Permission check on root
        if (!checkPermission(root, sender)) return

        // If there are args and the first arg matches a subcommand, recurse
        if (args.isNotEmpty()) {
            val sub = root.findSubcommand(args[0])
            if (sub != null) {
                dispatch(sub, sender, "$label ${args[0]}", args.copyOfRange(1, args.size))
                return
            }
            // Auto-generate help on "help"
            if (args[0].equals("help", ignoreCase = true)) {
                sendHelp(root, sender, label)
                return
            }
        }

        // No subcommand matched — execute this node
        val ctx = buildContext(root, sender, args)
        if (ctx == null) {
            sender.reply(BAD_USAGE.replace("{usage}", buildUsage(root, label)))
            return
        }
        executeNode(root, ctx, sender, label)
    }

    private fun checkPermission(
        node: CommandNode,
        sender: CommandSender,
    ): Boolean {
        val perm = node.permission ?: return true
        if (sender.hasPermission(perm)) return true
        sender.reply(NO_PERMISSION)
        return false
    }

    private fun buildContext(
        node: CommandNode,
        sender: CommandSender,
        args: Array<String>,
    ): CommandContext? {
        val parsed: MutableMap<String, Any?> = mutableMapOf()
        val required = node.arguments.filter { !it.optional }

        if (args.size < required.size) return null

        for ((index, arg) in node.arguments.withIndex()) {
            // Greedy: join all remaining tokens into one string
            if (arg.parser is GreedyStringParser) {
                parsed[arg.name] = if (index < args.size) args.drop(index).joinToString(" ") else ""
                break
            }
            val raw = args.getOrNull(index)
            if (raw == null) {
                if (arg.optional) continue else return null
            }
            val value = arg.parser.parse(raw)
            if (value == null && !arg.optional) return null
            parsed[arg.name] = value
        }

        @OptIn(SculkInternal::class)
        return CommandContext(sender, args, parsed)
    }

    private fun executeNode(
        node: CommandNode,
        ctx: CommandContext,
        sender: CommandSender,
        label: String,
    ) {
        when {
            node.anyExecutor != null -> node.anyExecutor!!.invoke(ctx)

            node.playerExecutor != null -> {
                if (sender !is Player) {
                    sender.reply(PLAYER_ONLY)
                    return
                }
                node.playerExecutor!!.invoke(ctx)
            }

            node.consoleExecutor != null -> {
                if (sender is Player) {
                    sender.reply(CONSOLE_ONLY)
                    return
                }
                node.consoleExecutor!!.invoke(ctx)
            }

            node.subcommands.isNotEmpty() -> {
                // Has subcommands but none matched — show help
                sendHelp(node, sender, label)
            }

            else -> sender.reply(BAD_USAGE.replace("{usage}", buildUsage(node, label)))
        }
    }

    private fun sendHelp(
        node: CommandNode,
        sender: CommandSender,
        label: String,
    ) {
        sender.reply("<gold><bold>/$label</bold></gold>")
        if (node.description.isNotBlank()) sender.reply("<gray>${node.description}")
        sender.reply(" ")
        for (sub in node.subcommands) {
            val perm = sub.permission
            if (perm != null && !sender.hasPermission(perm)) continue
            val desc = if (sub.description.isNotBlank()) " <dark_gray>— ${sub.description}" else ""
            sender.reply("<yellow>/$label ${sub.name}$desc")
        }
    }

    private fun buildUsage(
        node: CommandNode,
        label: String,
    ): String {
        val args =
            node.arguments.joinToString(" ") { arg ->
                if (arg.optional) "[${arg.name}]" else "<${arg.name}>"
            }
        return if (args.isBlank()) label else "$label $args"
    }
}
