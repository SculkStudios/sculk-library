package studio.sculk.discord

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.message.DiscordMessage

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
    private var nextId = 1L

    override var state: GatewayState = GatewayState.Disconnected
        private set

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

    override suspend fun channelExists(channel: ChannelId): SculkResult<Boolean> = guard { SculkResult.success(channel in knownChannels) }

    override suspend fun presence(activity: Presence): SculkResult<Unit> = guard {
        presences += activity
        SculkResult.ok()
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
