package studio.sculk.discord

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.discord.interaction.ComponentInteraction
import studio.sculk.discord.interaction.DeferredInteraction
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.discord.interaction.DiscordCommandContext
import studio.sculk.discord.interaction.Interaction
import studio.sculk.discord.interaction.Modal
import studio.sculk.discord.interaction.ModalInteraction
import studio.sculk.discord.interaction.OptionValue
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.discord.message.Text

/** Something a handler said back, and whether the channel could see it. */
@SculkStable
public data class Answer(public val message: DiscordMessage, public val ephemeral: Boolean) {
    /** The markdown of the first text component, which is what most assertions want. */
    public val text: String? get() = message.flatten().filterIsInstance<Text>().firstOrNull()?.markdown
}

/**
 * Records how an interaction was answered.
 *
 * Shared by every fake interaction below so a test asserts against one shape: what was said, whether
 * it was public, whether a modal was opened, and whether anything was said at all.
 *
 * That last one is the point. The failure this exists to catch is *silence* — a handler that returns
 * without answering leaves the user on "thinking…" until Discord times it out, which is
 * indistinguishable from the bot being dead and is where the historical bugs in this area lived.
 */
@SculkStable
public class InteractionRecorder {
    private val _answers = mutableListOf<Answer>()

    /** Everything said, in order. */
    public val answers: List<Answer> get() = _answers.toList()

    /** Modals opened, in order. */
    public val modals: MutableList<Modal> = mutableListOf()

    /** How many times something deferred. */
    public var deferrals: Int = 0
        internal set

    /** Messages the original was edited to. */
    public val edits: MutableList<DiscordMessage> = mutableListOf()

    /** True once the interaction has been answered or deferred. */
    public var acknowledged: Boolean = false
        internal set

    /** Set this and every call fails with it. */
    public var failure: String? = null

    /** The last thing said, or null — the common assertion, spelled once. */
    public val lastAnswer: Answer? get() = _answers.lastOrNull()

    /** True when the handler said nothing at all. */
    public val silent: Boolean get() = _answers.isEmpty() && modals.isEmpty()

    internal fun record(message: DiscordMessage, ephemeral: Boolean) {
        _answers += Answer(message, ephemeral)
    }
}

/**
 * The parts of an interaction every fake shares.
 *
 * Enforces the two rules a real gateway enforces, because a fake that is more permissive than
 * production lets a test pass on code Discord would reject: a modal only before acknowledgement, and
 * a reply only once.
 */
@SculkStable
public abstract class FakeInteraction(
    override val actor: DiscordActor,
    override val channel: ChannelId = ChannelId("1035829461209384960"),
    public val recorder: InteractionRecorder = InteractionRecorder(),
) : Interaction {
    override val guild: GuildId? get() = actor.guild
    override val acknowledged: Boolean get() = recorder.acknowledged

    override suspend fun reply(message: DiscordMessage): SculkResult<Unit> {
        recorder.failure?.let { return SculkResult.failure(it) }
        if (recorder.acknowledged) {
            return SculkResult.failure("This interaction was already answered; Discord rejects a second acknowledgement.")
        }
        recorder.acknowledged = true
        recorder.record(message, message.ephemeral)
        return SculkResult.ok()
    }

    override suspend fun replyModal(modal: Modal): SculkResult<Unit> {
        recorder.failure?.let { return SculkResult.failure(it) }
        if (recorder.acknowledged) {
            return SculkResult.failure(
                "This interaction was already answered, and Discord only accepts a modal as the first response.",
            )
        }
        recorder.acknowledged = true
        recorder.modals += modal
        return SculkResult.ok()
    }

    override suspend fun defer(ephemeral: Boolean): SculkResult<DeferredInteraction> {
        recorder.failure?.let { return SculkResult.failure(it) }
        recorder.acknowledged = true
        recorder.deferrals++
        return SculkResult.success(FakeDeferred(actor, recorder, ephemeral))
    }
}

/** What a handler holds after deferring. */
@SculkStable
public class FakeDeferred(
    override val actor: DiscordActor,
    public val recorder: InteractionRecorder,
    private val deferredEphemeral: Boolean,
) : DeferredInteraction {
    override suspend fun respond(message: DiscordMessage): SculkResult<Unit> {
        recorder.failure?.let { return SculkResult.failure(it) }
        recorder.record(message, message.ephemeral)
        return SculkResult.ok()
    }

    override suspend fun respond(markdown: String, ephemeral: Boolean): SculkResult<Unit> {
        recorder.failure?.let { return SculkResult.failure(it) }
        recorder.record(studio.sculk.discord.message.message { text(markdown) }, ephemeral)
        return SculkResult.ok()
    }

    override suspend fun editOriginal(message: DiscordMessage): SculkResult<Unit> {
        recorder.failure?.let { return SculkResult.failure(it) }
        recorder.edits += message
        return SculkResult.ok()
    }

    /** Whether the deferral itself was ephemeral, which Discord fixes at acknowledgement. */
    public val ephemeral: Boolean get() = deferredEphemeral
}

/**
 * A slash command being run.
 *
 * ```kotlin
 * val context = FakeCommandContext(actor, "kit give", mapOf("target" to FakeOption(user = someId)))
 * router.dispatch(context)
 * assertEquals("Given.", context.recorder.lastAnswer?.text)
 * ```
 */
@SculkStable
public class FakeCommandContext(
    actor: DiscordActor,
    override val path: String,
    private val options: Map<String, OptionValue> = emptyMap(),
    channel: ChannelId = ChannelId("1035829461209384960"),
    recorder: InteractionRecorder = InteractionRecorder(),
) : FakeInteraction(actor, channel, recorder),
    DiscordCommandContext {
    override fun optionOrNull(name: String): OptionValue? = options[name]
}

/** A button or select menu being used. */
@SculkStable
public class FakeComponentInteraction(
    actor: DiscordActor,
    override val componentId: ComponentId,
    override val messageId: MessageId = MessageId("9000000000000000000"),
    override val selected: List<String> = emptyList(),
    channel: ChannelId = ChannelId("1035829461209384960"),
    recorder: InteractionRecorder = InteractionRecorder(),
) : FakeInteraction(actor, channel, recorder),
    ComponentInteraction

/** A submitted modal. */
@SculkStable
public class FakeModalInteraction(
    actor: DiscordActor,
    override val modalId: ComponentId,
    private val fields: Map<String, String> = emptyMap(),
    channel: ChannelId = ChannelId("1035829461209384960"),
    recorder: InteractionRecorder = InteractionRecorder(),
) : FakeInteraction(actor, channel, recorder),
    ModalInteraction {
    override fun field(name: String): String? = fields[name]
}

/**
 * A command option's value.
 *
 * Each accessor throws unless the matching value was supplied, rather than returning a zero. Discord
 * validates the type before a handler sees it, so reading an option as the wrong type is a bug in the
 * handler — and a fake that answers `0` or `""` hides it behind a passing test.
 */
@SculkStable
public class FakeOption(
    private val string: String? = null,
    private val long: Long? = null,
    private val double: Double? = null,
    private val boolean: Boolean? = null,
    private val user: UserId? = null,
    private val channel: ChannelId? = null,
    private val role: RoleId? = null,
) : OptionValue {
    override val asString: String get() = string ?: wrong("a string")
    override val asLong: Long get() = long ?: wrong("an integer")
    override val asDouble: Double get() = double ?: wrong("a number")
    override val asBoolean: Boolean get() = boolean ?: wrong("a boolean")
    override val asUser: UserId get() = user ?: wrong("a user")
    override val asChannel: ChannelId get() = channel ?: wrong("a channel")
    override val asRole: RoleId get() = role ?: wrong("a role")

    private fun wrong(kind: String): Nothing = error("This FakeOption was not given $kind. Construct it with that value to read it as one.")
}
