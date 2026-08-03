package studio.sculk.discord

import kotlinx.coroutines.CoroutineScope
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.command.DiscordCommandSpec
import studio.sculk.discord.interaction.ComponentInteraction
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.message.DiscordMessage
import kotlin.time.Duration

/**
 * A connection to Discord.
 *
 * Backend-neutral: nothing in this file, or anywhere else in `sculk-discord`, names JDA. That is what
 * lets the library underneath be replaced without a consumer changing, and it is enforced by a test
 * rather than left to discipline — the 4.x hologram service imported PacketEvents straight past an
 * equivalent boundary, and the result was that ProtocolLib could not serve holograms at all.
 *
 * **Every call suspends and returns a [SculkResult].** There is no queue-it-later object to forget to
 * queue. That single decision removes the failure mode that cost the most to diagnose in the code
 * this replaces: a request that was built, never dispatched, and reported nothing.
 */
@SculkStable
public interface DiscordGateway : SculkHandle {
    public val state: GatewayState

    /** The bot's own user id, or null before the first successful connect. */
    public val selfId: UserId?

    /** Roles, nicknames and moderation — everything done to a member rather than to a channel. */
    public val guilds: GuildService

    /**
     * Connects, or reports why it could not.
     *
     * Suspends until the gateway is ready rather than returning the moment the socket opens: a caller
     * that sends immediately after a non-awaited connect is racing the handshake, and the send is
     * dropped with no indication it was ever attempted.
     */
    public suspend fun connect(): SculkResult<Unit>

    /** Sends [message] to [channel]. */
    public suspend fun send(channel: ChannelId, message: DiscordMessage): SculkResult<MessageId>

    /** One line of markdown, pinging nothing unless [mentions] says otherwise. */
    public suspend fun sendText(channel: ChannelId, markdown: String, mentions: Mentions = Mentions.None): SculkResult<MessageId> = send(
        channel,
        DiscordMessage(components = listOf(studio.sculk.discord.message.Text(markdown)), mentions = mentions),
    )

    /** Adds a reaction. [emoji] is a unicode character or a `name:id` custom emoji. */
    public suspend fun react(channel: ChannelId, message: MessageId, emoji: String): SculkResult<Unit>

    /** Replaces a message this bot sent. */
    public suspend fun edit(channel: ChannelId, message: MessageId, replacement: DiscordMessage): SculkResult<Unit>

    public suspend fun delete(channel: ChannelId, message: MessageId): SculkResult<Unit>

    /**
     * Resolves a channel, failing by name when it does not exist or the bot cannot see it.
     *
     * Those two are worth distinguishing in the message: a missing channel is a wrong id in a config,
     * and an invisible one is a permission the bot was never granted. They are fixed in different
     * places by different people.
     */
    public suspend fun channelExists(channel: ChannelId): SculkResult<Boolean>

    /** Sets the bot's presence line. */
    public suspend fun presence(activity: Presence): SculkResult<Unit>

    /**
     * Pushes a command set to Discord, replacing whatever was there.
     *
     * [guilds] empty registers globally, which Discord caches for about an hour — fine for a released
     * bot, painful while developing. Naming a guild registers instantly, which is why a dev config
     * usually names one.
     */
    public suspend fun registerCommands(commands: List<DiscordCommandSpec>, guilds: Set<GuildId> = emptySet()): SculkResult<Unit>

    /** Sends every incoming interaction through [router] until the handle is closed. */
    public fun route(router: InteractionRouter): SculkHandle

    /**
     * Calls [handler] for each message a human posts, until the handle is closed.
     *
     * Bot messages are filtered out before [handler] sees them, including this bot's own. A relay
     * that echoes what it just posted is an infinite loop with a rate limit as its only brake, and
     * every chat bridge writes that filter — so it lives here rather than in each of them.
     *
     * Requires [Intent.GuildMessages], and [Intent.MessageContent] for the body to be anything but
     * empty. `MessageContent` is privileged: without it Discord delivers the event with the text
     * stripped, which reads as a bot that sees messages and cannot understand them.
     */
    public fun onMessage(handler: suspend (DiscordChatMessage) -> Unit): SculkHandle

    /**
     * Waits for someone to use a component on [message].
     *
     * The thing discord.js has and JDA does not, and the reason a flow like "post a confirmation,
     * wait fifteen seconds, act on the answer" reads as three lines here instead of a listener, a
     * map keyed by message id, and a scheduled task to clean it up.
     *
     * Times out to a named failure rather than suspending forever — an abandoned collector that never
     * completes leaks its coroutine and the buttons stay live indefinitely.
     */
    public suspend fun awaitComponent(message: MessageId, within: Duration, from: UserId? = null): SculkResult<ComponentInteraction>
}

/** What the bot appears to be doing. */
@SculkStable
public data class Presence(public val text: String, public val kind: Kind = Kind.Playing) {
    @SculkStable
    public enum class Kind { Playing, Watching, Listening, Competing }
}

/**
 * What the gateway needs to connect.
 *
 * [intents] is explicit and has no default that quietly asks for privileged access. The two projects
 * this replaces disagreed — one requested `GuildMembers` and `MessageContent`, the other requested
 * nothing — and neither wrote down why. Asking for an intent you do not use is an approval step an
 * operator has to justify to Discord for a feature that was never built.
 */
@SculkStable
public data class BotConfig(
    public val token: String,
    public val intents: Set<Intent> = emptySet(),
    /** Guild ids to register commands into. Empty registers globally, which Discord caches for ~1 hour. */
    public val commandGuilds: Set<GuildId> = emptySet(),
) {
    /** False when the token is absent or still the placeholder a generated config ships with. */
    public val configured: Boolean
        get() = token.isNotBlank() && token !in PLACEHOLDERS

    private companion object {
        val PLACEHOLDERS = setOf("PASTE_BOT_TOKEN", "YOUR_TOKEN_HERE", "changeme")
    }
}

/**
 * A gateway intent.
 *
 * [GuildMembers] and [MessageContent] are privileged: Discord requires them to be enabled on the
 * application, and above 100 guilds requires review. Requesting one the bot does not use turns a
 * working deployment into a support conversation.
 */
@SculkStable
public enum class Intent {
    GuildMessages,
    DirectMessages,
    GuildMembers,
    MessageContent,
    GuildPresences,
    ;

    public val privileged: Boolean
        get() = this == GuildMembers || this == MessageContent || this == GuildPresences
}

/**
 * Creates a gateway for a backend.
 *
 * Implemented once per backend module and found by [SculkDiscord] through `META-INF/services`.
 */
@SculkStable
public interface DiscordGatewayProvider {
    /** For diagnostics — "JDA", say. */
    public val backend: String

    /** False when the backend's library is not on the classpath. */
    public fun isAvailable(): Boolean

    public fun create(config: BotConfig, scope: CoroutineScope): DiscordGateway
}
