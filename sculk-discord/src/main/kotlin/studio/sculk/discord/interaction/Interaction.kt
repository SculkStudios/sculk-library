package studio.sculk.discord.interaction

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.ChannelId
import studio.sculk.discord.ComponentId
import studio.sculk.discord.GuildId
import studio.sculk.discord.RoleId
import studio.sculk.discord.UserId
import studio.sculk.discord.message.DiscordMessage

/** Who triggered something, and what the server thinks of them. */
@SculkStable
public data class DiscordActor(
    public val id: UserId,
    public val name: String,
    public val guild: GuildId?,
    public val roles: Set<RoleId> = emptySet(),
    /** Raw Discord permission bits the member holds, or 0 outside a guild. */
    public val permissionBits: Long = 0,
) {
    public fun holds(permission: studio.sculk.discord.command.DiscordPermission): Boolean = permissionBits and permission.bit != 0L ||
        permissionBits and studio.sculk.discord.command.DiscordPermission.Administrator.bit != 0L
}

/**
 * An interaction that has not been answered yet.
 *
 * Discord discards an interaction that goes unanswered for three seconds, and the user sees a
 * permanent "the application did not respond". The two rules that follow used to live as comments:
 *
 * - **A modal must be the first response.** Discord rejects one after an acknowledgement, so
 *   [replyModal] exists on this type and not on [DeferredInteraction]. Reaching for a modal after
 *   deferring is now a compile error rather than a silent rejection at runtime.
 * - **Something must answer.** [defer] buys up to fifteen minutes. The router deferred this
 *   automatically if the handler had not answered within two seconds, so slow work is safe by
 *   default and "the application did not respond" stops being reachable by forgetting.
 */
@SculkStable
public interface Interaction {
    public val actor: DiscordActor
    public val channel: ChannelId
    public val guild: GuildId?

    /** True once this has been answered or deferred. Answering twice is rejected by Discord. */
    public val acknowledged: Boolean

    /** Answers immediately. */
    public suspend fun reply(message: DiscordMessage): SculkResult<Unit>

    /**
     * Asks for input.
     *
     * Only available before acknowledgement, because Discord only accepts it there. The modal's id
     * carries whatever the handler needs to remember, since Discord gives a modal no other way to
     * know which thing it belongs to.
     */
    public suspend fun replyModal(modal: Modal): SculkResult<Unit>

    /**
     * Buys time, and returns the handle that replies now go through.
     *
     * [ephemeral] is decided here and cannot change afterwards — Discord fixes the visibility at
     * acknowledgement.
     */
    public suspend fun defer(ephemeral: Boolean = true): SculkResult<DeferredInteraction>
}

/**
 * An acknowledged interaction.
 *
 * Replies go to the interaction's follow-up hook rather than to the interaction itself; a second
 * acknowledgement is rejected. That is why this is a separate type with no [Interaction.reply] on it
 * — the mistake it prevents cost a whole feature once, when a member `reply` shadowed an extension
 * of the same name and every message silently went nowhere.
 */
@SculkStable
public interface DeferredInteraction {
    public val actor: DiscordActor

    /** Sends a follow-up. Safe to call more than once. */
    public suspend fun respond(message: DiscordMessage): SculkResult<Unit>

    /** Convenience for the overwhelmingly common case: one line of markdown. */
    public suspend fun respond(markdown: String): SculkResult<Unit>

    /** Replaces the message the component that triggered this is attached to. */
    public suspend fun editOriginal(message: DiscordMessage): SculkResult<Unit>
}

/** A slash command being run. */
@SculkStable
public interface DiscordCommandContext : Interaction {
    /** The full path invoked, e.g. `kit give`. */
    public val path: String

    /** An option by name, or null when it was optional and omitted. */
    public fun optionOrNull(name: String): OptionValue?

    /** An option by name. Throws when absent, which for a required option is a wiring bug. */
    public fun option(name: String): OptionValue =
        optionOrNull(name) ?: error("Command '$path' has no option '$name'. Declared options are read by the name given in the spec.")
}

/** A resolved option value. Discord validated the type before this arrived. */
@SculkStable
public interface OptionValue {
    public val asString: String
    public val asLong: Long
    public val asDouble: Double
    public val asBoolean: Boolean
    public val asUser: UserId
    public val asChannel: ChannelId
    public val asRole: RoleId
}

/** A button or select menu being used. */
@SculkStable
public interface ComponentInteraction : Interaction {
    public val componentId: ComponentId

    /** The message the component sits on, so a handler can edit it in place. */
    public val messageId: studio.sculk.discord.MessageId

    /** Values chosen, for a select menu. Empty for a button. */
    public val selected: List<String>
}

/** A submitted modal. */
@SculkStable
public interface ModalInteraction : Interaction {
    public val modalId: ComponentId

    public fun field(name: String): String?
}
