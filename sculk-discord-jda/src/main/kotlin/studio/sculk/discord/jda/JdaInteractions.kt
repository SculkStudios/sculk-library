package studio.sculk.discord.jda

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.textinput.TextInput
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import studio.sculk.SculkResult
import studio.sculk.coroutine.await
import studio.sculk.discord.ChannelId
import studio.sculk.discord.ComponentId
import studio.sculk.discord.GuildId
import studio.sculk.discord.MessageId
import studio.sculk.discord.RoleId
import studio.sculk.discord.UserId
import studio.sculk.discord.interaction.ComponentInteraction
import studio.sculk.discord.interaction.DeferredInteraction
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.discord.interaction.DiscordCommandContext
import studio.sculk.discord.interaction.Interaction
import studio.sculk.discord.interaction.Modal
import studio.sculk.discord.interaction.ModalInteraction
import studio.sculk.discord.interaction.OptionValue
import studio.sculk.discord.interaction.TextFieldStyle
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.map
import net.dv8tion.jda.api.modals.Modal as JdaModal

/**
 * The shared half of every interaction: who, where, and how to answer.
 *
 * JDA models the three acknowledgement paths — reply, modal, defer-then-follow-up — as methods on one
 * type, so nothing stops a handler from taking two of them and having the second silently rejected.
 * Splitting them across [Interaction] and [DeferredInteraction] is the point of this wrapper.
 */
internal abstract class JdaInteraction(private val event: IReplyCallback) : Interaction {
    override val actor: DiscordActor = DiscordActor(
        id = UserId(event.user.id),
        name = event.member?.effectiveName ?: event.user.effectiveName,
        guild = event.guild?.let { GuildId(it.id) },
        roles = event.member?.roles?.map { RoleId(it.id) }?.toSet().orEmpty(),
        permissionBits = event.member?.let { Permission.getRaw(it.permissions) } ?: 0,
    )

    override val channel: ChannelId = ChannelId(event.channel?.id.orEmpty())
    override val guild: GuildId? = event.guild?.let { GuildId(it.id) }

    override val acknowledged: Boolean get() = event.isAcknowledged

    override suspend fun reply(message: DiscordMessage): SculkResult<Unit> = attempt("reply") {
        event.reply(message.toCreateData()).setEphemeral(message.ephemeral).submit().await()
    }

    override suspend fun replyModal(modal: Modal): SculkResult<Unit> {
        // Not every interaction can open one — a modal submit cannot answer with another modal — so
        // this is a capability check, not just a state check.
        val callback = event as? IModalCallback
            ?: return SculkResult.failure("A ${event::class.simpleName} cannot open a modal.")
        if (event.isAcknowledged) {
            return SculkResult.failure(
                "This interaction was already answered, and Discord only accepts a modal as the first " +
                    "response. Open the modal before doing any work that might acknowledge.",
            )
        }
        return attempt("open the modal") { callback.replyModal(modal.toJda()).submit().await() }
    }

    override suspend fun defer(ephemeral: Boolean): SculkResult<DeferredInteraction> {
        if (event.isAcknowledged) return SculkResult.success(JdaDeferred(event, actor))
        return attempt("defer") { event.deferReply(ephemeral).submit().await() }
            .map { JdaDeferred(event, actor) }
    }
}

internal class JdaDeferred(private val event: IReplyCallback, override val actor: DiscordActor) : DeferredInteraction {
    override suspend fun respond(message: DiscordMessage): SculkResult<Unit> = attempt("send the follow-up") {
        event.hook.sendMessage(message.toCreateData()).setEphemeral(message.ephemeral).submit().await()
    }

    override suspend fun respond(markdown: String): SculkResult<Unit> = respond(
        studio.sculk.discord.message.message {
            text(markdown)
            ephemeral = true
        },
    )

    override suspend fun editOriginal(message: DiscordMessage): SculkResult<Unit> = attempt("edit the original") {
        event.hook.editOriginalComponents(message.toTopLevelComponents()).useComponentsV2().submit().await()
    }
}

internal class JdaCommandContext(private val event: SlashCommandInteractionEvent) :
    JdaInteraction(event),
    DiscordCommandContext {
    /** `kit give`, matching the path a spec flattens to. */
    override val path: String = listOfNotNull(event.name, event.subcommandGroup, event.subcommandName).joinToString(" ")

    override fun optionOrNull(name: String): OptionValue? = event.getOption(name)?.let(::JdaOptionValue)
}

internal class JdaComponentInteraction(private val event: GenericComponentInteractionCreateEvent, override val componentId: ComponentId) :
    JdaInteraction(event),
    ComponentInteraction {
    override val messageId: MessageId = MessageId(event.messageId)

    override val selected: List<String> = (event as? StringSelectInteractionEvent)?.values.orEmpty()
}

internal class JdaModalInteraction(private val event: ModalInteractionEvent, override val modalId: ComponentId) :
    JdaInteraction(event),
    ModalInteraction {
    override fun field(name: String): String? = event.getValue(name)?.asString
}

private class JdaOptionValue(private val mapping: OptionMapping) : OptionValue {
    override val asString: String get() = mapping.asString
    override val asLong: Long get() = mapping.asLong
    override val asDouble: Double get() = mapping.asDouble
    override val asBoolean: Boolean get() = mapping.asBoolean
    override val asUser: UserId get() = UserId(mapping.asUser.id)
    override val asChannel: ChannelId get() = ChannelId(mapping.asChannel.id)
    override val asRole: RoleId get() = RoleId(mapping.asRole.id)
}

internal fun Modal.toJda(): JdaModal = JdaModal.create(id.encoded, title)
    .addComponents(
        fields.map { field ->
            val input = TextInput.create(field.name, field.style.toJda())
                .setRequired(field.required)
                .apply {
                    field.value?.let { setValue(it) }
                    field.placeholder?.let { setPlaceholder(it.take(TextInput.MAX_PLACEHOLDER_LENGTH)) }
                    field.maxLength?.let { setMaxLength(it) }
                }.build()
            Label.of(field.label, input)
        },
    ).build()

private fun TextFieldStyle.toJda(): TextInputStyle = when (this) {
    TextFieldStyle.Short -> TextInputStyle.SHORT
    TextFieldStyle.Paragraph -> TextInputStyle.PARAGRAPH
}

/**
 * Turns a REST call into a result instead of a throw.
 *
 * Every one of these is a network round trip that can fail for reasons the caller can do nothing
 * about — a deleted channel, a revoked permission, an expired interaction. Throwing from inside a
 * handler leaves the user on "thinking…" until Discord times it out.
 */
private inline fun <T> attempt(what: String, block: () -> T): SculkResult<Unit> = runCatching { block() }.fold(
    { SculkResult.ok() },
    { SculkResult.failure("Could not $what: ${it.message ?: it::class.simpleName}", it) },
)
