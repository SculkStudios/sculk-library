package studio.sculk.discord

import studio.sculk.annotation.SculkStable

/**
 * A Discord id.
 *
 * Distinct types per kind rather than one `Snowflake`, because every id Discord hands out is the same
 * shape and passing a channel id where a user id was wanted is otherwise a compiling, silent mistake
 * — the config keys carrying them (`mainGuildId`, `staffGuildId`, `alertChannelId`) sit next to each
 * other and are copied between fields by hand.
 */
@SculkStable
public sealed interface DiscordId {
    public val raw: String
}

@SculkStable
@JvmInline
public value class GuildId(override val raw: String) : DiscordId {
    override fun toString(): String = raw
}

@SculkStable
@JvmInline
public value class ChannelId(override val raw: String) : DiscordId {
    override fun toString(): String = raw
}

@SculkStable
@JvmInline
public value class UserId(override val raw: String) : DiscordId {
    /** The `<@id>` mention form. Rendering it does not make it ping — see [Mentions]. */
    public val mention: String get() = "<@$raw>"

    override fun toString(): String = raw
}

@SculkStable
@JvmInline
public value class RoleId(override val raw: String) : DiscordId {
    public val mention: String get() = "<@&$raw>"

    override fun toString(): String = raw
}

@SculkStable
@JvmInline
public value class MessageId(override val raw: String) : DiscordId {
    override fun toString(): String = raw
}

/**
 * True when [raw] could be a Discord id at all.
 *
 * Shape only — Discord alone knows whether the id exists. Worth checking because the usual failure is
 * an operator pasting a channel *name* into a config field that wanted its id, and the resulting
 * "channel not visible to the bot" reads as a permissions problem for as long as it takes someone to
 * look at the value.
 */
@SculkStable
public fun DiscordId.isWellFormed(): Boolean = raw.length in 17..20 && raw.all { it in '0'..'9' }
