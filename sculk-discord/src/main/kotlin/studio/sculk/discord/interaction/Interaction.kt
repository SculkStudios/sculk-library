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
    /**
     * What to call them here — the nickname if they set one, otherwise their display or account name.
     *
     * Right for anything shown to a person. Wrong for anything stored or matched, because it changes
     * whenever they do, and differs between guilds for the same account: see [username].
     */
    public val name: String,
    public val guild: GuildId?,
    public val roles: Set<RoleId> = emptySet(),
    /** Raw Discord permission bits the member holds, or 0 outside a guild. */
    public val permissionBits: Long = 0,
    /**
     * The account handle, which is global and does not change per guild.
     *
     * Kept apart from [name] because collapsing the two loses the only stable half. A bridge that
     * records "who linked this account" wants this; a bridge rendering a chat line wants [name]. The
     * old single field forced every consumer to pick one meaning and be wrong for the other use.
     *
     * Defaults to [name] so a hand-built actor in a test need not state both.
     */
    public val username: String = name,
    /** Their nickname in this guild, or null when they have not set one. */
    public val nickname: String? = null,
    /** Their avatar, for a relay that posts through a webhook and wants the face to match. */
    public val avatarUrl: String? = null,
) {
    public fun holds(permission: studio.sculk.discord.command.DiscordPermission): Boolean = permissionBits and permission.bit != 0L ||
        permissionBits and studio.sculk.discord.command.DiscordPermission.Administrator.bit != 0L

    /**
     * The union of what [grants] confers on every role this member holds.
     *
     * Every consumer mapping Discord roles onto its own permission nodes writes this same fold, so it
     * lives here once.
     *
     * Note what it deliberately does *not* do: a member with no mapped role gets an empty set, never a
     * fallback to their in-game rank or to Discord's own Administrator bit. Being server owner in
     * Discord grants nothing here. The operator states which roles confer which nodes and nothing
     * else does, so a power over players is granted on purpose or not at all.
     *
     * ```kotlin
     * val nodes = actor.permissionsFor(settings.roles)   // Map<RoleId, Set<String>>
     * if ("myplugin.punish.ban" !in nodes) return refuse()
     * ```
     */
    public fun <T> permissionsFor(grants: Map<RoleId, Set<T>>): Set<T> = roles.flatMapTo(mutableSetOf()) { grants[it].orEmpty() }
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

    /**
     * One line of markdown, the overwhelmingly common case.
     *
     * [ephemeral] defaults to true because most of these name a player. Set it false for the one
     * line that has to be public — a channel notice saying an action was taken, which is what stops
     * a second moderator acting on the same alert.
     */
    public suspend fun respond(markdown: String, ephemeral: Boolean = true): SculkResult<Unit>

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

    /**
     * A user or a role, whichever they picked.
     *
     * `Mentionable` is the one option type where Discord decides which kind arrived, so the caller has
     * to branch on it — hence the shared supertype rather than two accessors, one of which would throw.
     */
    public val asMentionable: studio.sculk.discord.DiscordId

    /** The uploaded file. */
    public val asAttachment: studio.sculk.discord.DiscordAttachment
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

/**
 * Says something, whichever side of acknowledgement this interaction is on.
 *
 * A handler cannot always know whether it has been deferred: the router's watchdog defers on the
 * handler's behalf after two seconds, so whether [reply] is still legal depends on how slow the
 * database was. Picking wrong means the message is rejected and the user is left on "thinking..."
 * forever, which reads as the bot being dead. Trying the direct reply and falling back to a
 * follow-up covers both without the caller tracking state.
 *
 * ```kotlin
 * executes { answer("Banned ${'$'}{target.name}.") }
 * ```
 */
@SculkStable
public suspend fun Interaction.answer(markdown: String, ephemeral: Boolean = true) {
    val message = studio.sculk.discord.message.message {
        text(markdown)
        this.ephemeral = ephemeral
    }
    if (!acknowledged && reply(message).isSuccess) return
    runCatching { defer(ephemeral = ephemeral).getOrNull()?.respond(message) }
}
