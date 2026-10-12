package studio.sculk.command

import net.kyori.adventure.text.Component
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.text.Text

/**
 * What an executor sees: who ran the command, the values of its arguments, and a way to answer.
 * Runs in the sender's execution context; see [CommandSource.executionContext].
 */
@SculkStable
public class CommandContext internal constructor(
    public val source: CommandSource,
    /** The full input, without the leading slash. */
    public val input: String,
    /** The command name or alias the sender typed. */
    public val label: String,
    private val values: Map<ArgumentDeclaration, Any?>,
    public val text: Text,
) {
    /**
     * The value of [argument]. An optional argument the sender left out reads as its default, or
     * null. Reading an argument that is not on the path that ran is a wiring bug and throws.
     */
    public operator fun <T> get(argument: ArgumentHandle<T>): T {
        val declaration = argument.declaration
        val value = when {
            declaration in values -> values[declaration]
            declaration.optional -> declaration.default
            else -> throw IllegalStateException("$argument is not part of the command path that ran.")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    @SculkStable
    public companion object {
        /**
         * A context for a platform compiler that has read the argument [values] itself. Arguments
         * left out are absent from [values] and read as their defaults.
         */
        @SculkInternal
        public fun of(
            source: CommandSource,
            input: String,
            label: String,
            values: Map<ArgumentHandle<*>, Any?>,
            text: Text,
        ): CommandContext = CommandContext(source, input, label, values.mapKeys { it.key.declaration }, text)
    }

    /** True when the sender typed [argument], rather than leaving an optional one out. */
    public fun isPresent(argument: ArgumentHandle<*>): Boolean = argument.declaration in values

    /** Sends [template], rendered with the plugin's theme, to the sender. Values are never parsed as markup. */
    public fun reply(template: String, vararg values: Pair<String, Any?>) {
        source.audience.sendMessage(text.render(template, *values))
    }

    public fun reply(message: Component) {
        source.audience.sendMessage(message)
    }

    /** Stops the command and shows [template] to the sender as its outcome. */
    public fun fail(template: String, vararg values: Pair<String, Any?>): Nothing =
        throw CommandFailedException(text.render(template, *values))
}

/**
 * Thrown by [CommandContext.fail] to end a command with a message. Not an error: the dispatcher
 * shows the message and logs nothing.
 */
@SculkStable
public class CommandFailedException(public val reason: Component) : RuntimeException(null, null, false, false)

/** The templates Sculk uses for command outcomes it produces itself. Plugins replace them to match their voice. */
@SculkStable
public class CommandMessages(
    public val unknownCommand: String = "<danger>Unknown command. <muted>Type /help for a list.",
    public val incompleteCommand: String = "<danger>Incomplete command. <muted>Usage: <usage>",
    public val invalidArgument: String = "<danger>Invalid <argument>: expected <expected>, found <found>.",
    public val playersOnly: String = "<danger>Only players can run this command.",
    public val cooldown: String = "<warning>You can use this again in <remaining>.",
    public val confirm: String = "<warning>Run the command again within <window> to confirm.",
    public val internalError: String = "<danger>Something went wrong running this command; the server log has the details.",
) {
    @SculkStable
    public companion object {
        public val DEFAULT: CommandMessages = CommandMessages()
    }
}
