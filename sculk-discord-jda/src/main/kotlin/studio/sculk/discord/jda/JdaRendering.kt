package studio.sculk.discord.jda

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.container.ContainerChildComponent
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.SelectOption
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import studio.sculk.discord.Mentions
import studio.sculk.discord.message.DiscordMessage
import studio.sculk.discord.message.Divider
import studio.sculk.discord.message.EntityKind
import studio.sculk.discord.message.Interactive
import studio.sculk.discord.message.MessageComponent
import studio.sculk.discord.message.Row
import studio.sculk.discord.message.SectionAccessory
import studio.sculk.discord.message.Text
import java.awt.Color
import studio.sculk.discord.message.Button as SculkButton
import studio.sculk.discord.message.Container as SculkContainer
import studio.sculk.discord.message.EntitySelect as SculkEntitySelect
import studio.sculk.discord.message.MediaGallery as SculkGallery
import studio.sculk.discord.message.Section as SculkSection
import studio.sculk.discord.message.SelectMenu as SculkSelect
import studio.sculk.discord.message.Thumbnail as SculkThumbnail

// The whole translation layer, in one file of pure functions. Kept out of the gateway so that what
// a message turns into is asserted against a value rather than against a live connection.

/**
 * Builds the JDA payload for a message.
 *
 * `useComponentsV2` is unconditional. Mixing the two systems in one bot means two visual languages
 * for the same brand, and the V2 container is the only one that can hold buttons *inside* the
 * coloured block rather than in a detached row underneath it.
 */
internal fun DiscordMessage.toCreateData(): MessageCreateData = MessageCreateBuilder()
    .useComponentsV2()
    .setComponents(components.map { it.toTopLevel() })
    .setAllowedMentions(mentions.toParseTypes())
    .apply {
        (mentions as? Mentions.Allow)?.let { allow ->
            mentionUsers(allow.users.map { it.raw })
            mentionRoles(allow.roles.map { it.raw })
        }
    }.build()

/**
 * Which mention kinds Discord may resolve.
 *
 * An empty collection is not the same as leaving this unset: unset means "resolve whatever the text
 * says", which is how an `@everyone` typed by a player pings a staff server through an alert about
 * them typing it.
 */
internal fun Mentions.toParseTypes(): Collection<Message.MentionType> = when (this) {
    Mentions.None -> emptyList()

    Mentions.All -> listOf(Message.MentionType.USER, Message.MentionType.ROLE, Message.MentionType.EVERYONE)

    is Mentions.Allow -> buildList {
        // USER and ROLE are listed so the explicit id lists below are honoured; without the type
        // present, a named id is still not resolved.
        if (users.isNotEmpty()) add(Message.MentionType.USER)
        if (roles.isNotEmpty()) add(Message.MentionType.ROLE)
        if (everyone) add(Message.MentionType.EVERYONE)
    }
}

/** The component list on its own, for the edit paths that take components rather than a whole message. */
internal fun DiscordMessage.toTopLevelComponents(): List<MessageTopLevelComponent> = components.map { it.toTopLevel() }

private fun MessageComponent.toTopLevel(): MessageTopLevelComponent = when (this) {
    is Text -> TextDisplay.of(markdown)
    is Divider -> separator(large)
    is Row -> ActionRow.of(components.map { it.toJda() })
    is SculkSection -> toJda()
    is SculkGallery -> toJda()
    is SculkContainer -> toJda()
    is Interactive -> ActionRow.of(toJda())
}

private fun SculkContainer.toJda(): Container {
    val children = children.map { child ->
        when (child) {
            is Text -> TextDisplay.of(child.markdown)

            is Divider -> separator(child.large)

            is Row -> ActionRow.of(child.components.map { it.toJda() })

            is SculkSection -> child.toJda()

            is SculkGallery -> child.toJda()

            is Interactive -> ActionRow.of(child.toJda())

            // Unreachable: Container's own init refuses this. Kept because the compiler cannot see
            // that, and an error() here is a better failure than a silent branch if it ever changes.
            is SculkContainer -> error("A container inside a container is not something Discord renders.")
        } as ContainerChildComponent
    }
    val container = Container.of(children).withSpoiler(spoiler)
    return accentRgb?.let { container.withAccentColor(Color(it)) } ?: container
}

private fun SculkSection.toJda(): Section = Section.of(accessory.toJda(), content.map { TextDisplay.of(it.markdown) })

private fun SectionAccessory.toJda(): SectionAccessoryComponent = when (this) {
    is SculkButton -> toJda()

    is SculkThumbnail -> Thumbnail.fromUrl(url)
        .let { thumb -> description?.let(thumb::withDescription) ?: thumb }
        .withSpoiler(spoiler)
}

private fun SculkGallery.toJda(): MediaGallery = MediaGallery.of(
    items.map { item ->
        MediaGalleryItem.fromUrl(item.url)
            .let { media -> item.description?.let(media::withDescription) ?: media }
            .withSpoiler(item.spoiler)
    },
)

private fun separator(large: Boolean): Separator = Separator.createDivider(if (large) Separator.Spacing.LARGE else Separator.Spacing.SMALL)

private fun Interactive.toJda(): net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent = when (this) {
    is SculkButton -> toJda()
    is SculkSelect -> toJda()
    is SculkEntitySelect -> toJda()
}

private fun SculkEntitySelect.toJda(): EntitySelectMenu = EntitySelectMenu
    .create(id.encoded, kinds.map { it.toJda() })
    .setPlaceholder(placeholder)
    .setRequiredRange(minChoices, maxChoices)
    .setDisabled(!enabled)
    .build()

private fun EntityKind.toJda(): EntitySelectMenu.SelectTarget = when (this) {
    EntityKind.User -> EntitySelectMenu.SelectTarget.USER
    EntityKind.Role -> EntitySelectMenu.SelectTarget.ROLE
    EntityKind.Channel -> EntitySelectMenu.SelectTarget.CHANNEL
}

private fun SculkButton.toJda(): Button {
    val button = link?.let { Button.link(it, label) }
        ?: Button.of(style.toJda(), id!!.encoded, label)
    val withEmoji = emoji?.let { button.withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromFormatted(it)) } ?: button
    return withEmoji.withDisabled(!enabled)
}

private fun studio.sculk.discord.message.ButtonStyle.toJda(): ButtonStyle = when (this) {
    studio.sculk.discord.message.ButtonStyle.Primary -> ButtonStyle.PRIMARY
    studio.sculk.discord.message.ButtonStyle.Secondary -> ButtonStyle.SECONDARY
    studio.sculk.discord.message.ButtonStyle.Success -> ButtonStyle.SUCCESS
    studio.sculk.discord.message.ButtonStyle.Danger -> ButtonStyle.DANGER
}

private fun SculkSelect.toJda(): StringSelectMenu = StringSelectMenu.create(id.encoded)
    .addOptions(
        options.map { option ->
            SelectOption.of(option.label, option.value)
                .withDescription(option.description)
                .withDefault(option.default)
                .let { built -> option.emoji?.let { built.withEmoji(Emoji.fromFormatted(it)) } ?: built }
        },
    ).setPlaceholder(placeholder)
    .setRequiredRange(minChoices, maxChoices)
    .setDisabled(!enabled)
    .build()
