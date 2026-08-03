package studio.sculk.discord

import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.command.DiscordCommandSpec
import studio.sculk.discord.interaction.ComponentInteraction
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.message.DiscordMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** One message the fake was asked to send. */
@SculkStable
public data class SentMessage(public val channel: ChannelId, public val message: DiscordMessage, public val id: MessageId)

/**
 * A gateway that records instead of connecting.
 *
 * This is what makes a bot testable: message content, which buttons an alert offers, whether a
 * mention policy was set, and whether anything was sent at all are all assertions against a list,
 * with no token, no network and no Discord.
 *
 * ```kotlin
 * val gateway = FakeDiscordGateway()
 * alerts.post(incident)
 * assertEquals(1, gateway.sent.size)
 * assertEquals(Mentions.None, gateway.sent.single().message.mentions)
 * ```
 *
 * It refuses what it cannot model rather than guessing — [failure] makes every call fail, and
 * sending before [connect] fails the way a real gateway does, because a test that passes against a
 * fake which is more forgiving than production has proved nothing.
 */
@SculkStable
public class FakeDiscordGateway(override var selfId: UserId? = UserId("100000000000000000")) : DiscordGateway {
    private val _sent = mutableListOf<SentMessage>()
    private val _edited = mutableListOf<SentMessage>()
    private val _deleted = mutableListOf<MessageId>()
    private val _registeredCommands = mutableListOf<DiscordCommandSpec>()
    private var nextId = 1L

    override var state: GatewayState = GatewayState.Disconnected
        private set

    /** Records moderation. Declare members on it with [FakeGuildService.put] before acting on them. */
    override val guilds: FakeGuildService = FakeGuildService()

    /** Reactions added, as channel/message/emoji. */
    public val reactions: MutableList<Triple<ChannelId, MessageId, String>> = mutableListOf()

    /** Set this and every call fails with it, for testing the caller's fallback path. */
    public var failure: String? = null

    /** Channels [channelExists] reports as present. Anything else is reported missing. */
    public val knownChannels: MutableSet<ChannelId> = mutableSetOf()

    /** Presences set, in order. */
    public val presences: MutableList<Presence> = mutableListOf()

    public val sent: List<SentMessage> get() = _sent.toList()
    public val edited: List<SentMessage> get() = _edited.toList()
    public val deleted: List<MessageId> get() = _deleted.toList()

    public var closed: Boolean = false
        private set

    /** The last message sent, or null. The common assertion, spelled once. */
    public val lastSent: DiscordMessage? get() = _sent.lastOrNull()?.message

    /** Commands pushed to Discord, and where they were pushed. */
    public val registeredCommands: List<DiscordCommandSpec> get() = _registeredCommands.toList()

    public var commandGuilds: Set<GuildId> = emptySet()
        private set

    /** Routers attached, so a test can assert the bot wired its handlers up. */
    public val routers: MutableList<InteractionRouter> = mutableListOf()

    /** Message handlers attached. Feed them with [deliver]. */
    public val messageHandlers: MutableList<suspend (DiscordChatMessage) -> Unit> = mutableListOf()

    /** Set this and the next [awaitComponent] resolves to it instead of timing out. */
    public var nextComponent: ComponentInteraction? = null

    /** Messages a collector waited on, in order. */
    public val awaited: MutableList<MessageId> = mutableListOf()

    public fun clear() {
        _sent.clear()
        _edited.clear()
        _deleted.clear()
        presences.clear()
    }

    override suspend fun connect(): SculkResult<Unit> = guard(requireReady = false) {
        state = GatewayState.Ready
        SculkResult.ok()
    }

    override suspend fun send(channel: ChannelId, message: DiscordMessage): SculkResult<MessageId> = guard {
        val id = MessageId((nextId++).toString())
        _sent += SentMessage(channel, message, id)
        SculkResult.success(id)
    }

    override suspend fun edit(channel: ChannelId, message: MessageId, replacement: DiscordMessage): SculkResult<Unit> = guard {
        _edited += SentMessage(channel, replacement, message)
        SculkResult.ok()
    }

    override suspend fun delete(channel: ChannelId, message: MessageId): SculkResult<Unit> = guard {
        _deleted += message
        SculkResult.ok()
    }

    override suspend fun react(channel: ChannelId, message: MessageId, emoji: String): SculkResult<Unit> = guard {
        reactions += Triple(channel, message, emoji)
        SculkResult.ok()
    }

    override suspend fun channelExists(channel: ChannelId): SculkResult<Boolean> = guard { SculkResult.success(channel in knownChannels) }

    override suspend fun presence(activity: Presence): SculkResult<Unit> = guard {
        presences += activity
        SculkResult.ok()
    }

    override suspend fun registerCommands(commands: List<DiscordCommandSpec>, guilds: Set<GuildId>): SculkResult<Unit> = guard {
        _registeredCommands.clear()
        _registeredCommands += commands
        commandGuilds = guilds
        SculkResult.ok()
    }

    override fun route(router: InteractionRouter): SculkHandle {
        routers += router
        return SculkHandle { routers -= router }
    }

    override fun onMessage(handler: suspend (DiscordChatMessage) -> Unit): SculkHandle {
        messageHandlers += handler
        return SculkHandle { messageHandlers -= handler }
    }

    /**
     * Delivers [message] as if Discord had, applying the same bot filter a real gateway does.
     *
     * The filter is applied here rather than assumed away, because a fake that happily delivers the
     * bot's own messages would let an echo loop pass its test and only appear in production.
     */
    public suspend fun deliver(message: DiscordChatMessage) {
        if (message.fromBot) return
        messageHandlers.toList().forEach { it(message) }
    }

    /**
     * Returns whatever [nextComponent] was set to, or times out.
     *
     * Null rather than suspending is deliberate: a fake that blocks forever turns a wrong assertion
     * into a hung test suite, and the timeout path is the one worth covering anyway.
     */
    override suspend fun awaitComponent(message: MessageId, within: Duration, from: UserId?): SculkResult<ComponentInteraction> = guard {
        awaited += message
        nextComponent?.let { SculkResult.success(it) }
            ?: SculkResult.failure("Nobody used a component on $message within $within.")
    }

    override fun close() {
        closed = true
        state = GatewayState.Disconnected
    }

    /**
     * Applies the two states a real gateway would refuse in.
     *
     * [connect] is exempt from the readiness check for the obvious reason, so the check lives here
     * behind a flag rather than being repeated in five methods with one of them eventually missing it.
     */
    private inline fun <T> guard(requireReady: Boolean = true, block: () -> SculkResult<T>): SculkResult<T> {
        failure?.let { return SculkResult.failure(it) }
        if (closed) return SculkResult.failure("This gateway was closed.")
        if (requireReady && !state.usable) {
            return SculkResult.failure("The gateway is $state, not Ready. Call connect() first.")
        }
        return block()
    }
}

/** One thing the bot did to a member, so a test can assert it happened exactly once. */
@SculkStable
public sealed interface GuildAction {
    public val guild: GuildId
    public val user: UserId

    public data class AddRole(override val guild: GuildId, override val user: UserId, val role: RoleId) : GuildAction

    public data class RemoveRole(override val guild: GuildId, override val user: UserId, val role: RoleId) : GuildAction

    public data class SetRoles(override val guild: GuildId, override val user: UserId, val roles: Set<RoleId>) : GuildAction

    public data class SetNickname(override val guild: GuildId, override val user: UserId, val nickname: String?) : GuildAction

    public data class Kick(override val guild: GuildId, override val user: UserId, val reason: String?) : GuildAction

    public data class Ban(override val guild: GuildId, override val user: UserId, val reason: String?, val deleteMessageHours: Int) :
        GuildAction

    public data class Unban(override val guild: GuildId, override val user: UserId) : GuildAction

    public data class Timeout(override val guild: GuildId, override val user: UserId, val duration: Duration, val reason: String?) :
        GuildAction

    public data class ClearTimeout(override val guild: GuildId, override val user: UserId) : GuildAction
}

/**
 * Records moderation instead of performing it.
 *
 * Members must be declared with [put] before they can be acted on. A fake that happily bans a user it
 * has never heard of lets a test pass against a lookup that would fail in production — the same
 * reason `FakeRepository` refuses to filter without `columnsOf`.
 */
@SculkStable
public class FakeGuildService : GuildService {
    private val members = mutableMapOf<Pair<String, String>, DiscordActor>()

    /** Every action taken, in order. */
    public val actions: MutableList<GuildAction> = mutableListOf()

    /** Set this and every call fails with it. */
    public var failure: String? = null

    /** Declares a member the bot can see. */
    public fun put(guild: GuildId, actor: DiscordActor) {
        members[guild.raw to actor.id.raw] = actor
    }

    override suspend fun member(guild: GuildId, user: UserId): SculkResult<DiscordActor> {
        failure?.let { return SculkResult.failure(it) }
        return members[guild.raw to user.raw]?.let { SculkResult.success(it) }
            ?: SculkResult.failure("User ${user.raw} is not a known member of ${guild.raw}.")
    }

    override suspend fun isPresent(guild: GuildId): Boolean = members.keys.any { it.first == guild.raw }

    override suspend fun addRole(guild: GuildId, user: UserId, role: RoleId): SculkResult<Unit> =
        record(guild, user, GuildAction.AddRole(guild, user, role))

    override suspend fun removeRole(guild: GuildId, user: UserId, role: RoleId): SculkResult<Unit> =
        record(guild, user, GuildAction.RemoveRole(guild, user, role))

    override suspend fun setRoles(guild: GuildId, user: UserId, roles: Set<RoleId>): SculkResult<Unit> =
        record(guild, user, GuildAction.SetRoles(guild, user, roles))

    override suspend fun setNickname(guild: GuildId, user: UserId, nickname: String?): SculkResult<Unit> =
        record(guild, user, GuildAction.SetNickname(guild, user, nickname))

    override suspend fun kick(guild: GuildId, user: UserId, reason: String?): SculkResult<Unit> =
        record(guild, user, GuildAction.Kick(guild, user, reason))

    override suspend fun ban(guild: GuildId, user: UserId, reason: String?, deleteMessageHours: Int): SculkResult<Unit> {
        if (deleteMessageHours !in 0..GuildService.MAX_DELETE_HOURS) {
            return SculkResult.failure("deleteMessageHours must be 0..${GuildService.MAX_DELETE_HOURS}, got $deleteMessageHours.")
        }
        // A ban does not require membership: banning someone who already left is a real operation.
        failure?.let { return SculkResult.failure(it) }
        actions += GuildAction.Ban(guild, user, reason, deleteMessageHours)
        return SculkResult.ok()
    }

    override suspend fun unban(guild: GuildId, user: UserId): SculkResult<Unit> {
        failure?.let { return SculkResult.failure(it) }
        actions += GuildAction.Unban(guild, user)
        return SculkResult.ok()
    }

    override suspend fun timeout(guild: GuildId, user: UserId, duration: Duration, reason: String?): SculkResult<Unit> {
        if (duration <= Duration.ZERO || duration > GuildService.MAX_TIMEOUT_DAYS.days) {
            return SculkResult.failure("A timeout must be between zero and ${GuildService.MAX_TIMEOUT_DAYS} days, got $duration.")
        }
        return record(guild, user, GuildAction.Timeout(guild, user, duration, reason))
    }

    override suspend fun clearTimeout(guild: GuildId, user: UserId): SculkResult<Unit> =
        record(guild, user, GuildAction.ClearTimeout(guild, user))

    private fun record(guild: GuildId, user: UserId, action: GuildAction): SculkResult<Unit> {
        failure?.let { return SculkResult.failure(it) }
        if ((guild.raw to user.raw) !in members) {
            return SculkResult.failure(
                "User ${user.raw} is not a known member of ${guild.raw}. Declare them with put() first — a fake " +
                    "that acts on unknown members hides a lookup that would fail in production.",
            )
        }
        actions += action
        return SculkResult.ok()
    }
}
