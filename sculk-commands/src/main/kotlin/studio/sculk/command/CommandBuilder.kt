@file:JvmName("SculkCommands")

package studio.sculk.command

import org.bukkit.entity.Player
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.command.argument.ArgumentParser
import studio.sculk.command.argument.BooleanParser
import studio.sculk.command.argument.BoundedDoubleParser
import studio.sculk.command.argument.BoundedIntParser
import studio.sculk.command.argument.BoundedLongParser
import studio.sculk.command.argument.ChoiceParser
import studio.sculk.command.argument.DoubleParser
import studio.sculk.command.argument.DurationParser
import studio.sculk.command.argument.GreedyStringParser
import studio.sculk.command.argument.IntParser
import studio.sculk.command.argument.LongParser
import studio.sculk.command.argument.MaterialParser
import studio.sculk.command.argument.PlayerParser
import studio.sculk.command.argument.StringParser
import studio.sculk.command.argument.UuidParser
import studio.sculk.command.argument.WorldParser
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Predicate

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
constructor(name: String) {
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

    /**
     * Aliases for this command.
     *
     * ```kotlin
     * command("teleport") {
     *     aliases = listOf("tp", "tele")
     *     player { /* ... */ }
     * }
     * ```
     */
    public var aliases: List<String>
        get() = node.aliases
        set(value) {
            node.aliases.clear()
            node.aliases.addAll(value)
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
    public fun player(block: suspend CommandContext.() -> Unit) {
        node.playerExecutor = block
    }

    /**
     * Executes when the command is run from the server console.
     *
     * Player senders are automatically rejected with an error message.
     */
    public fun console(block: suspend CommandContext.() -> Unit) {
        node.consoleExecutor = block
    }

    /**
     * Executes regardless of sender type (player or console).
     *
     * Use when the command behaviour is identical for all senders.
     * Prefer [player] or [console] for sender-specific logic.
     */
    public fun executes(block: suspend CommandContext.() -> Unit) {
        node.anyExecutor = block
    }

    // -----------------------------------------------------------------------
    // Java-friendly executor overloads (Consumer<CommandContext>)
    // -----------------------------------------------------------------------

    /**
     * Java-friendly overload of [player]. Executes when a [Player] runs the command.
     *
     * ```java
     * command.player(ctx -> ctx.reply("<green>Hello, " + ctx.getPlayer().getName()));
     * ```
     */
    @SculkStable
    public fun player(block: Consumer<CommandContext>) {
        node.playerExecutor = { block.accept(this) }
    }

    /** Java-friendly overload of [console]. Executes when the console runs the command. */
    @SculkStable
    public fun console(block: Consumer<CommandContext>) {
        node.consoleExecutor = { block.accept(this) }
    }

    /** Java-friendly overload of [executes]. Executes for any sender. */
    @SculkStable
    public fun executes(block: Consumer<CommandContext>) {
        node.anyExecutor = { block.accept(this) }
    }

    /** Applies a per-sender cooldown to this command node. */
    public fun cooldown(key: String, duration: Duration) {
        node.cooldown = CooldownDefinition(key, duration.toMillis())
    }

    /**
     * Adds a pre-execution filter. Filters run in registration order before the
     * executor; returning `false` aborts dispatch. The filter should message the
     * sender when it rejects.
     *
     * ```kotlin
     * command("admin") {
     *     middleware { ctx ->
     *         logger.info("${ctx.sender.name} ran /admin")
     *         true
     *     }
     *     executes { reply("<green>ok") }
     * }
     * ```
     */
    public fun middleware(block: suspend (CommandContext) -> Boolean) {
        node.middleware += block
    }

    /**
     * Java-friendly overload of [middleware] taking a [Predicate]. Returning `false` aborts dispatch.
     */
    @SculkStable
    public fun middleware(block: Predicate<CommandContext>) {
        node.middleware += { ctx -> block.test(ctx) }
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
    public fun sub(name: String, block: CommandBuilder.() -> Unit) {
        val child = CommandBuilder(name).apply(block)
        node.subcommands += child.node
    }

    /**
     * Java-friendly overload of [sub] taking a [Consumer].
     *
     * ```java
     * command.sub("reload", sub -> sub.player(ctx -> ctx.reply("<green>Reloaded.")));
     * ```
     */
    @SculkStable
    public fun sub(name: String, block: Consumer<CommandBuilder>) {
        val child = CommandBuilder(name)
        block.accept(child)
        node.subcommands += child.node
    }

    // -----------------------------------------------------------------------
    // Arguments
    // -----------------------------------------------------------------------

    /** Registers a required string argument with [name]. */
    @JvmOverloads
    public fun string(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, StringParser, optional)
    }

    /** Registers an integer argument with [name]. */
    @JvmOverloads
    public fun int(name: String, optional: Boolean = false, min: Int? = null, max: Int? = null) {
        val parser = if (min == null && max == null) IntParser else BoundedIntParser(min, max)
        node.arguments += ArgumentDefinition(name, parser, optional)
    }

    /** Registers a double argument with [name]. */
    @JvmOverloads
    public fun double(name: String, optional: Boolean = false, min: Double? = null, max: Double? = null) {
        val parser = if (min == null && max == null) DoubleParser else BoundedDoubleParser(min, max)
        node.arguments += ArgumentDefinition(name, parser, optional)
    }

    /** Registers a boolean argument with [name]. */
    @JvmOverloads
    public fun boolean(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, BooleanParser, optional)
    }

    /** Registers an online-player argument with [name]. */
    @JvmOverloads
    public fun player(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, PlayerParser, optional)
    }

    /** Registers a long-integer argument with [name]. */
    @JvmOverloads
    public fun long(name: String, optional: Boolean = false, min: Long? = null, max: Long? = null) {
        val parser = if (min == null && max == null) LongParser else BoundedLongParser(min, max)
        node.arguments += ArgumentDefinition(name, parser, optional)
    }

    /** Registers a UUID argument with [name]. */
    @JvmOverloads
    public fun uuid(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, UuidParser, optional)
    }

    /** Registers a world argument with [name]. */
    @JvmOverloads
    public fun world(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, WorldParser, optional)
    }

    /** Registers a material argument with [name]. */
    @JvmOverloads
    public fun material(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, MaterialParser, optional)
    }

    /** Registers a duration argument with [name], accepting values like `10s`, `5m`, or `1h`. */
    @JvmOverloads
    public fun duration(name: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, DurationParser, optional)
    }

    /** Registers an enum argument with [name]. */
    public inline fun <reified E : Enum<E>> enum(name: String, optional: Boolean = false) {
        argument(name, ChoiceParser(enumValues<E>().map { it.name.lowercase() }), optional)
    }

    /**
     * Java-friendly overload of [enum] taking a [Class] token.
     *
     * ```java
     * command.enum("mode", GameMode.class);
     * ```
     */
    @JvmOverloads
    @SculkStable
    public fun <E : Enum<E>> enum(name: String, type: Class<E>, optional: Boolean = false) {
        argument(name, ChoiceParser(type.enumConstants.map { it.name.lowercase() }), optional)
    }

    /**
     * Registers a greedy string argument that consumes the rest of the input as one string.
     *
     * Must be the last argument on this command node.
     *
     * ```kotlin
     * greedy("message")
     * executes { reply(argument<String>("message")) }
     * ```
     */
    public fun greedy(name: String) {
        node.arguments += ArgumentDefinition(name, GreedyStringParser, optional = false)
    }

    /** Registers a fixed-choice argument with [name] accepting only [choices]. */
    @JvmOverloads
    public fun choice(name: String, vararg choices: String, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, ChoiceParser(choices.toList()), optional)
    }

    /**
     * Registers a custom argument with [name] using [parser].
     *
     * ```kotlin
     * object WorldParser : ArgumentParser<World> {
     *     override val typeName = "world"
     *     override fun parse(input: String) = Bukkit.getWorld(input)
     *     override fun suggest(input: String) = Bukkit.getWorlds().map { it.name }
     * }
     *
     * command("spawn") {
     *     argument("world", WorldParser)
     *     player {
     *         val world = argument<World>("world")
     *         player!!.teleport(world.spawnLocation)
     *     }
     * }
     * ```
     */
    @JvmOverloads
    public fun <T : Any> argument(name: String, parser: ArgumentParser<T>, optional: Boolean = false) {
        node.arguments += ArgumentDefinition(name, parser, optional)
    }
}

/**
 * Creates and returns a [CommandBuilder] with [name].
 *
 * The returned builder must be registered via [studio.sculk.platform.SculkPlatform]
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
public fun command(name: String, block: CommandBuilder.() -> Unit): CommandBuilder = CommandBuilder(name).apply(block)

/**
 * Java-friendly overload of [command] taking a [Consumer].
 *
 * ```java
 * CommandBuilder cmd = SculkCommands.command("sculk", b -> {
 *     b.setPermission("sculk.admin");
 *     b.sub("ping", sub -> sub.executes(ctx -> ctx.reply("<gray>Pong!")));
 * });
 * ```
 */
@SculkStable
public fun command(name: String, block: Consumer<CommandBuilder>): CommandBuilder = CommandBuilder(name).also { block.accept(it) }
