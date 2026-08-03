package studio.sculk.discord.command

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.DiscordCommandContext

/** Builds a [DiscordCommandSpec]. */
@SculkStable
public class DiscordCommandBuilder internal constructor(private val name: String) {
    public var description: String = ""
    public var defaultPermission: DiscordPermission? = null
    public var guildOnly: Boolean = true

    /** Replies visible only to the caller. On by default: most of these name a player. */
    public var ephemeral: Boolean = true

    private val options = mutableListOf<CommandOption>()
    private val children = mutableListOf<DiscordCommandSpec>()
    private var executor: (suspend DiscordCommandContext.() -> Unit)? = null

    public fun executes(block: suspend DiscordCommandContext.() -> Unit) {
        executor = block
    }

    public fun sub(name: String, block: DiscordCommandBuilder.() -> Unit) {
        children += DiscordCommandBuilder(name).apply(block).build()
    }

    /** Adds an already-built child, for composing a command tree across files. */
    public fun child(spec: DiscordCommandSpec) {
        children += spec
    }

    public fun string(name: String, description: String, required: Boolean = false, choices: List<OptionChoice> = emptyList()): Unit =
        add(CommandOption(name, description, OptionType.String, required, choices))

    /** A string option whose suggestions are resolved per keystroke rather than captured once. */
    public fun string(
        name: String,
        description: String,
        required: Boolean = false,
        autocomplete: suspend (typed: String) -> List<OptionChoice>,
    ): Unit = add(CommandOption(name, description, OptionType.String, required, autocomplete = autocomplete))

    public fun integer(name: String, description: String, required: Boolean = false, min: Long? = null, max: Long? = null): Unit = add(
        CommandOption(name, description, OptionType.Integer, required, minValue = min?.toDouble(), maxValue = max?.toDouble()),
    )

    public fun number(name: String, description: String, required: Boolean = false, min: Double? = null, max: Double? = null): Unit =
        add(CommandOption(name, description, OptionType.Number, required, minValue = min, maxValue = max))

    public fun boolean(name: String, description: String, required: Boolean = false): Unit =
        add(CommandOption(name, description, OptionType.Boolean, required))

    public fun user(name: String, description: String, required: Boolean = false): Unit =
        add(CommandOption(name, description, OptionType.User, required))

    public fun channel(name: String, description: String, required: Boolean = false): Unit =
        add(CommandOption(name, description, OptionType.Channel, required))

    public fun role(name: String, description: String, required: Boolean = false): Unit =
        add(CommandOption(name, description, OptionType.Role, required))

    public fun attachment(name: String, description: String, required: Boolean = false): Unit =
        add(CommandOption(name, description, OptionType.Attachment, required))

    private fun add(option: CommandOption) {
        require(options.none { it.name == option.name }) {
            "Duplicate option name '${option.name}' on /$name."
        }
        options += option
    }

    internal fun build(): DiscordCommandSpec = DiscordCommandSpec(
        name = name,
        description = description.ifBlank { "No description." },
        options = options.toList(),
        children = children.toList(),
        defaultPermission = defaultPermission,
        guildOnly = guildOnly,
        ephemeral = ephemeral,
        executor = executor,
    )
}

/**
 * Declares a slash command. The result is data; registering it is a separate step.
 *
 * Same shape as the Brigadier-side `command { }` on purpose — the two are the same idea against
 * different front ends, and a developer who has written one should not have to learn a second dialect
 * to write the other.
 */
@SculkStable
public fun discordCommand(name: String, block: DiscordCommandBuilder.() -> Unit): DiscordCommandSpec =
    DiscordCommandBuilder(name).apply(block).build()
