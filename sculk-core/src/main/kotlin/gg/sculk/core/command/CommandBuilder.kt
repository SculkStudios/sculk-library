package gg.sculk.core.command

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.command.argument.BooleanParser
import gg.sculk.core.command.argument.ChoiceParser
import gg.sculk.core.command.argument.DoubleParser
import gg.sculk.core.command.argument.IntParser
import gg.sculk.core.command.argument.PlayerParser
import gg.sculk.core.command.argument.StringParser
import org.bukkit.entity.Player

/**
 * DSL builder for a [CommandNode].
 *
 * Obtain an instance by calling the [command] top-level function.
 *
 * ```kotlin
 * command("sculk") {
 *     permission = "sculk.admin"
 *
 *     sub("reload") {
 *         player { reply("<green>Reloaded.") }
 *     }
 *
 *     sub("ping") {
 *         executes { reply("<gray>Pong!") }
 *     }
 * }
 * ```
 */
@SculkStable
public class CommandBuilder
    @SculkInternal
    constructor(
        name: String,
    ) {
        @SculkInternal
        public val node: CommandNode = CommandNode(name)

        /** The permission required to run this command or subcommand. */
        public var permission: String?
            get() = node.permission
            set(value) {
                node.permission = value
            }

        /** A short description shown in auto-generated help. */
        public var description: String
            get() = node.description
            set(value) {
                node.description = value
            }

        // -----------------------------------------------------------------------
        // Sender-type executors
        // -----------------------------------------------------------------------

        /**
         * Executes when the command is run by a [Player].
         *
         * The [CommandContext.player] property is guaranteed non-null inside this block.
         * Console senders are automatically rejected with an error message.
         */
        public fun player(block: CommandContext.() -> Unit) {
            node.playerExecutor = block
        }

        /**
         * Executes when the command is run from the server console.
         *
         * Player senders are automatically rejected with an error message.
         */
        public fun console(block: CommandContext.() -> Unit) {
            node.consoleExecutor = block
        }

        /**
         * Executes regardless of sender type (player or console).
         *
         * Use when the command behaviour is identical for all senders.
         * Prefer [player] or [console] for sender-specific logic.
         */
        public fun executes(block: CommandContext.() -> Unit) {
            node.anyExecutor = block
        }

        // -----------------------------------------------------------------------
        // Subcommands
        // -----------------------------------------------------------------------

        /**
         * Registers a subcommand with [name].
         *
         * ```kotlin
         * sub("give") {
         *     player {
         *         val target = argument<Player>("target")
         *         reply("Gave something to ${target.name}")
         *     }
         * }
         * ```
         */
        public fun sub(
            name: String,
            block: CommandBuilder.() -> Unit,
        ) {
            val child = CommandBuilder(name).apply(block)
            node.subcommands += child.node
        }

        // -----------------------------------------------------------------------
        // Arguments
        // -----------------------------------------------------------------------

        /** Registers a required string argument with [name]. */
        public fun string(
            name: String,
            optional: Boolean = false,
        ) {
            node.arguments += ArgumentDefinition(name, StringParser, optional)
        }

        /** Registers an integer argument with [name]. */
        public fun int(
            name: String,
            optional: Boolean = false,
        ) {
            node.arguments += ArgumentDefinition(name, IntParser, optional)
        }

        /** Registers a double argument with [name]. */
        public fun double(
            name: String,
            optional: Boolean = false,
        ) {
            node.arguments += ArgumentDefinition(name, DoubleParser, optional)
        }

        /** Registers a boolean argument with [name]. */
        public fun boolean(
            name: String,
            optional: Boolean = false,
        ) {
            node.arguments += ArgumentDefinition(name, BooleanParser, optional)
        }

        /** Registers an online-player argument with [name]. */
        public fun player(
            name: String,
            optional: Boolean = false,
        ) {
            node.arguments += ArgumentDefinition(name, PlayerParser, optional)
        }

        /** Registers a fixed-choice argument with [name] accepting only [choices]. */
        public fun choice(
            name: String,
            vararg choices: String,
            optional: Boolean = false,
        ) {
            node.arguments += ArgumentDefinition(name, ChoiceParser(choices.toList()), optional)
        }
    }

/**
 * Creates and returns a [CommandBuilder] with [name].
 *
 * The returned builder must be registered via [gg.sculk.platform.SculkPlatform]
 * or via the `command {}` DSL function available inside a `SculkPlatform.create` block.
 *
 * Standalone usage (registered manually later):
 * ```kotlin
 * val myCommand = command("sculk") {
 *     permission = "sculk.admin"
 *     sub("ping") { executes { reply("Pong!") } }
 * }
 * ```
 */
@SculkStable
public fun command(
    name: String,
    block: CommandBuilder.() -> Unit,
): CommandBuilder = CommandBuilder(name).apply(block)
