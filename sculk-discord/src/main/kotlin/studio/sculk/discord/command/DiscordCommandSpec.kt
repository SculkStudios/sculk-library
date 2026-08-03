package studio.sculk.discord.command

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.DiscordCommandContext

/**
 * What kind of value an option holds.
 *
 * Discord parses and validates these client-side, so a wrong type never reaches the handler — which
 * is why the framework does not ship string parsers for them the way the Brigadier side has to.
 */
@SculkStable
public enum class OptionType {
    String,
    Integer,
    Number,
    Boolean,
    User,
    Channel,
    Role,
    Mentionable,
    Attachment,
}

/**
 * One option on a command.
 *
 * [choices] and [autocomplete] are mutually exclusive — Discord rejects a command declaring both, and
 * finding that out at registration time means every command in the batch fails, not just this one.
 *
 * [autocomplete] is a lambda rather than a captured list for the same reason `choice` is on the
 * Brigadier side: a snapshot taken at registration stops matching the moment a config reload changes
 * the set, and the suggestions then stay wrong until the next restart.
 */
@SculkStable
public data class CommandOption(
    public val name: String,
    public val description: String,
    public val type: OptionType,
    public val required: Boolean = false,
    public val choices: List<OptionChoice> = emptyList(),
    public val autocomplete: (suspend (typed: kotlin.String) -> List<OptionChoice>)? = null,
    public val minValue: Double? = null,
    public val maxValue: Double? = null,
) {
    init {
        require(name.matches(NAME)) { "Option name '$name' must be 1-32 lowercase characters, digits, '_' or '-'." }
        require(description.isNotBlank() && description.length <= MAX_DESCRIPTION) {
            "Option description must be 1-$MAX_DESCRIPTION characters, got ${description.length}."
        }
        require(choices.isEmpty() || autocomplete == null) {
            "Option '$name' declares both fixed choices and an autocomplete handler; Discord accepts one or the other."
        }
        require(choices.size <= MAX_CHOICES) { "An option offers at most $MAX_CHOICES fixed choices, got ${choices.size}." }
    }

    /** `<name>` or `[name]`, matching the Brigadier side's usage rendering. */
    public val usage: kotlin.String get() = if (required) "<$name>" else "[$name]"

    @SculkStable
    public companion object {
        public const val MAX_CHOICES: Int = 25
        public const val MAX_DESCRIPTION: Int = 100
        internal val NAME = Regex("[a-z0-9_-]{1,32}")
    }
}

@SculkStable
public data class OptionChoice(public val name: String, public val value: String)

/**
 * A slash command as data — no backend types in its shape.
 *
 * The same split the Brigadier side uses, for the same payoff: usage strings, the permission filter
 * and the shape of the tree are all testable with no gateway, and only the adapter needs a live
 * connection. It also means a command list can be diffed before it is pushed, which matters because
 * Discord caches a global registration for about an hour.
 *
 * ```kotlin
 * discordCommand("kit") {
 *     description = "Claim a kit"
 *     sub("give") {
 *         description = "Give a kit to someone"
 *         user("target", "Who gets it", required = true)
 *         string("kit", "Which kit") { kits.names().map { OptionChoice(it, it) } }
 *         executes { respond("Given.") }
 *     }
 * }
 * ```
 */
@SculkStable
public data class DiscordCommandSpec(
    public val name: String,
    public val description: String = "",
    public val options: List<CommandOption> = emptyList(),
    public val children: List<DiscordCommandSpec> = emptyList(),
    /** A Discord permission bit the *member* must hold. Null means anyone the channel lets in. */
    public val defaultPermission: DiscordPermission? = null,
    /** Refuse in DMs. Almost always right for anything touching a server. */
    public val guildOnly: Boolean = true,
    /** Reply visible only to the caller. The safe default for anything naming a player. */
    public val ephemeral: Boolean = true,
    public val executor: (suspend DiscordCommandContext.() -> Unit)? = null,
) {
    init {
        require(name.matches(CommandOption.NAME)) {
            "Command name '$name' must be 1-32 lowercase characters, digits, '_' or '-'. Discord rejects uppercase."
        }
        require(children.isEmpty() || options.isEmpty()) {
            "Command '$name' has both subcommands and its own options. Discord allows one or the other: a node " +
                "with children is a group, and only leaves take options."
        }
        require(children.isEmpty() || executor == null) {
            "Command '$name' has both subcommands and its own handler, which Discord cannot represent — a group " +
                "is never invoked directly. Move the handler onto a subcommand."
        }
        require(options.count { it.required } == options.takeWhile { it.required }.size) {
            "On '$name', a required option follows an optional one. Discord requires every required option first."
        }
        require(depth() <= MAX_DEPTH) {
            "Command '$name' nests ${depth()} levels deep; Discord allows $MAX_DEPTH " +
                "(command, group, subcommand). Flatten the deepest level into its parent."
        }
    }

    public val executable: Boolean get() = executor != null

    /** How many levels this tree has, counting itself. */
    public fun depth(): Int = 1 + (children.maxOfOrNull { it.depth() } ?: 0)

    /** The usage line, e.g. `/kit give <target> [kit]`. */
    public fun usage(parentPath: String = ""): String {
        val path = if (parentPath.isEmpty()) name else "$parentPath $name"
        val rendered = options.joinToString(" ") { it.usage }
        return if (rendered.isEmpty()) "/$path" else "/$path $rendered"
    }

    /** Every invokable node with its full path, so `/help` and a registration diff read the same list. */
    public fun flatten(parentPath: String = ""): List<Pair<String, DiscordCommandSpec>> {
        val path = if (parentPath.isEmpty()) name else "$parentPath $name"
        val here = if (executable) listOf(path to this) else emptyList()
        return here + children.flatMap { it.flatten(path) }
    }

    /** The node at a full path such as `kit give`, or null. */
    public fun at(fullPath: String): DiscordCommandSpec? {
        val parts = fullPath.trim().split(" ").filter { it.isNotEmpty() }
        if (parts.isEmpty() || !parts.first().equals(name, ignoreCase = true)) return null
        var node = this
        for (part in parts.drop(1)) {
            node = node.children.firstOrNull { it.name.equals(part, ignoreCase = true) } ?: return null
        }
        return node
    }

    @SculkStable
    public companion object {
        /** Command, subcommand group, subcommand. Discord has no fourth level. */
        public const val MAX_DEPTH: Int = 3
    }
}

/**
 * A Discord member permission.
 *
 * Deliberately not an in-game permission node. The person running a slash command is usually not on
 * the server, so there is no live permissible to ask — and inheriting authority from an in-game rank
 * the operator has forgotten about is how someone ends up with powers nobody granted them on purpose.
 */
@SculkStable
public enum class DiscordPermission(public val bit: Long) {
    ManageMessages(1L shl 13),
    KickMembers(1L shl 1),
    BanMembers(1L shl 2),
    ModerateMembers(1L shl 40),
    ManageGuild(1L shl 5),
    Administrator(1L shl 3),
}
