package studio.sculk.core.command.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import studio.sculk.core.adventure.reply
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.core.command.CommandContext
import studio.sculk.core.command.CommandNode
import studio.sculk.core.command.CooldownStore
import studio.sculk.core.command.argument.ArgumentParser
import studio.sculk.core.command.argument.GreedyStringParser
import studio.sculk.core.coroutine.SculkCoroutineScope
import com.mojang.brigadier.context.CommandContext as BrigadierContext

/**
 * Compiles a Sculk [CommandNode] tree into a Paper Brigadier [LiteralCommandNode].
 *
 * The DSL stays the same; this swaps the execution engine to Paper's native command system so
 * commands get client-side tab completion, error highlighting, and prior-argument-aware
 * suggestions. Executors are suspend functions launched on [scope]'s main dispatcher, so a command
 * can do non-blocking IO (`withAsync { ... }`) without freezing the server.
 */
@SculkInternal
public class CommandCompiler(
    private val scope: SculkCoroutineScope,
    private val cooldowns: CooldownStore = CooldownStore(),
) {
    /** Compiles [node] and its full subtree into a registrable Brigadier node. */
    public fun compile(node: CommandNode): LiteralCommandNode<CommandSourceStack> = buildLiteral(node).build()

    private fun buildLiteral(node: CommandNode): LiteralArgumentBuilder<CommandSourceStack> {
        val literal = Commands.literal(node.name)
        node.permission?.let { perm -> literal.requires { source -> source.sender.hasPermission(perm) } }
        node.subcommands.forEach { child -> literal.then(buildLiteral(child)) }

        val command = makeCommand(node)
        if (node.arguments.isEmpty()) {
            if (hasExecutor(node)) literal.executes(command)
        } else {
            if (node.arguments.all { it.optional } && hasExecutor(node)) literal.executes(command)
            literal.then(buildArgument(node, 0, command))
        }
        return literal
    }

    private fun buildArgument(
        node: CommandNode,
        index: Int,
        command: Command<CommandSourceStack>,
    ): RequiredArgumentBuilder<CommandSourceStack, *> {
        val definition = node.arguments[index]
        val greedy = definition.parser is GreedyStringParser

        @Suppress("UNCHECKED_CAST")
        val argument =
            Commands.argument(
                definition.name,
                SculkArgumentType(definition.parser as ArgumentParser<Any>, greedy),
            )

        val isLast = index == node.arguments.lastIndex
        val remainingOptional = node.arguments.drop(index + 1).all { it.optional }
        if ((isLast || remainingOptional) && hasExecutor(node)) argument.executes(command)
        if (!isLast) argument.then(buildArgument(node, index + 1, command))
        return argument
    }

    private fun makeCommand(node: CommandNode): Command<CommandSourceStack> =
        Command { ctx ->
            dispatch(node, ctx)
            Command.SINGLE_SUCCESS
        }

    private fun dispatch(
        node: CommandNode,
        ctx: BrigadierContext<CommandSourceStack>,
    ) {
        val sender = ctx.source.sender
        val parsed = LinkedHashMap<String, Any?>()
        for (definition in node.arguments) {
            val value = runCatching { ctx.getArgument(definition.name, Any::class.java) }.getOrNull()
            if (value != null) parsed[definition.name] = value
        }

        @OptIn(SculkInternal::class)
        val context =
            CommandContext(
                sender,
                ctx.input
                    .trim()
                    .split(" ")
                    .toTypedArray(),
                parsed,
            )

        val cooldown = node.cooldown
        if (cooldown != null) {
            val remaining = cooldowns.tryAcquire("${cooldown.key}:${senderId(sender)}", cooldown.durationMillis)
            if (remaining != null) {
                val seconds = (remaining / MILLIS_PER_SECOND).coerceAtLeast(1L)
                sender.reply("<red>Please wait <yellow>${seconds}s</yellow> before using this again.")
                return
            }
        }

        scope.launchMain {
            try {
                for (filter in node.middleware) {
                    if (!filter(context)) return@launchMain
                }
                runExecutor(node, context, sender)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                sender.reply("<red>An error occurred while running this command.")
                throw error
            }
        }
    }

    private suspend fun runExecutor(
        node: CommandNode,
        context: CommandContext,
        sender: CommandSender,
    ) {
        when {
            node.anyExecutor != null -> node.anyExecutor!!.invoke(context)
            node.playerExecutor != null -> {
                if (sender !is Player) {
                    sender.reply(PLAYER_ONLY)
                    return
                }
                node.playerExecutor!!.invoke(context)
            }
            node.consoleExecutor != null -> {
                if (sender is Player) {
                    sender.reply(CONSOLE_ONLY)
                    return
                }
                node.consoleExecutor!!.invoke(context)
            }
        }
    }

    private fun hasExecutor(node: CommandNode): Boolean =
        node.anyExecutor != null || node.playerExecutor != null || node.consoleExecutor != null

    private fun senderId(sender: CommandSender): String = if (sender is Player) sender.uniqueId.toString() else "console"

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val PLAYER_ONLY = "<red>This command can only be used by players."
        const val CONSOLE_ONLY = "<red>This command can only be used from the console."
    }
}
