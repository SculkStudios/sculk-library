package studio.sculk.discord.message

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.ComponentId

/**
 * A piece of a Discord message, as data.
 *
 * Backend-free on purpose. A message can be built, inspected and asserted on with no gateway, which
 * is what makes "does this alert offer a Ban button to someone without the permission" a unit test
 * rather than something you find out in a live channel. One renderer turns this into whatever the
 * backend speaks.
 */
@SculkStable
public sealed interface MessageComponent

/** Markdown. Discord renders it; it is never parsed as MiniMessage. */
@SculkStable
public data class Text(public val markdown: String) : MessageComponent

/** A horizontal rule. */
@SculkStable
public data class Divider(public val large: Boolean = false) : MessageComponent

/**
 * A row of interactive components.
 *
 * Discord allows five per row; more is rejected when the row is built rather than by the API on
 * send, so the failure names the row instead of arriving as a 400 from a queued request.
 */
@SculkStable
public data class Row(public val components: List<Interactive>) : MessageComponent {
    init {
        require(components.isNotEmpty()) { "An action row with no components is rejected by Discord." }
        require(components.size <= MAX_PER_ROW) {
            "An action row holds at most $MAX_PER_ROW components, got ${components.size}."
        }
    }

    @SculkStable
    public companion object {
        public const val MAX_PER_ROW: Int = 5
    }
}

/**
 * A bordered block with an accent colour down its edge.
 *
 * The Components V2 replacement for an embed, and the reason the model does not have an `Embed` type:
 * a container nests real components, so a message with buttons under a coloured block is one tree
 * rather than an embed plus a detached action row that only looks attached.
 */
@SculkStable
public data class Container(
    public val children: List<MessageComponent>,
    /** `0xRRGGBB`, or null for no accent. Usually [studio.sculk.text.ThemeStyle.swatchHex]. */
    public val accentRgb: Int? = null,
    /** Renders blurred until clicked. */
    public val spoiler: Boolean = false,
) : MessageComponent {
    init {
        require(children.isNotEmpty()) { "A container with no children renders as an empty block." }
        require(accentRgb == null || accentRgb in 0x000000..0xFFFFFF) {
            "Accent must be a 24-bit RGB value, got ${accentRgb?.toString(16)}."
        }
        // Caught here rather than at send. Discord does not nest containers, and finding that out from
        // a rejected request means the failure names an HTTP call instead of the line that built it —
        // and only for the messages that actually get sent, not the ones a test builds.
        require(children.none { it is Container }) {
            "A container cannot hold another container; Discord does not render one. Flatten the " +
                "children, or use a Divider to separate sections within the one container."
        }
    }
}

/** Something that can sit to the right of a [Section]. */
@SculkStable
public sealed interface SectionAccessory

/**
 * An image beside text.
 *
 * Only ever a [Section] accessory — Discord has no standalone thumbnail — which is why this is not a
 * [MessageComponent] and cannot be added to a message on its own.
 *
 * The reason a chat bridge wants Components V2 at all: a relayed line with the speaker's face next to
 * it is a section whose accessory is their avatar, and there was no way to express that with an embed
 * short of the author-icon slot, which is one per embed and cannot repeat down a message.
 */
@SculkStable
public data class Thumbnail(
    public val url: String,
    /** Alt text. Shown on hover and read by screen readers. */
    public val description: String? = null,
    public val spoiler: Boolean = false,
) : SectionAccessory {
    init {
        require(url.isNotBlank()) { "A thumbnail needs a URL." }
        require(description == null || description.length <= MAX_DESCRIPTION) {
            "A thumbnail description is at most $MAX_DESCRIPTION characters, got ${description?.length}."
        }
    }

    @SculkStable
    public companion object {
        public const val MAX_DESCRIPTION: Int = 1024
    }
}

/**
 * Text with something beside it.
 *
 * The layout an embed could only fake: up to three lines of markdown on the left, one accessory on the
 * right — an image, or a button that acts on whatever the text is about. A row of buttons under a
 * block of text is a different thing, and reads as a different thing; use [Row] for that.
 */
@SculkStable
public data class Section(public val content: List<Text>, public val accessory: SectionAccessory) : MessageComponent {
    init {
        require(content.isNotEmpty()) { "A section with no text renders as a stray accessory." }
        require(content.size <= MAX_CONTENT) {
            "A section holds at most $MAX_CONTENT lines of text, got ${content.size}. Use a container " +
                "with several sections for more."
        }
    }

    @SculkStable
    public companion object {
        public const val MAX_CONTENT: Int = 3
    }
}

/** One image in a [MediaGallery]. */
@SculkStable
public data class MediaItem(public val url: String, public val description: String? = null, public val spoiler: Boolean = false) {
    init {
        require(url.isNotBlank()) { "A media item needs a URL." }
    }
}

/**
 * A grid of images.
 *
 * What a relay posts when somebody attaches four screenshots: one gallery rather than four messages or
 * four embeds, which is both what Discord shows for a native upload and what stops a bridge turning
 * one post into a wall.
 */
@SculkStable
public data class MediaGallery(public val items: List<MediaItem>) : MessageComponent {
    init {
        require(items.isNotEmpty()) { "A media gallery with no items renders as nothing." }
        require(items.size <= MAX_ITEMS) { "A media gallery holds at most $MAX_ITEMS items, got ${items.size}." }
    }

    @SculkStable
    public companion object {
        public const val MAX_ITEMS: Int = 10
    }
}

/** Something a user can click or choose from. */
@SculkStable
public sealed interface Interactive : MessageComponent

@SculkStable
public enum class ButtonStyle { Primary, Secondary, Success, Danger }

/**
 * A button.
 *
 * Either it has a [ComponentId] and reports clicks, or it is a [link] and navigates — Discord has no
 * button that does both, and a link button never produces an interaction.
 */
@SculkStable
public data class Button(
    public val label: String,
    public val id: ComponentId? = null,
    public val link: String? = null,
    public val style: ButtonStyle = ButtonStyle.Secondary,
    public val enabled: Boolean = true,
    public val emoji: String? = null,
) : Interactive,
    SectionAccessory {
    init {
        require((id == null) != (link == null)) {
            "A button carries either a component id or a link, never both and never neither — " +
                "a link button produces no interaction, so an id on one would never fire."
        }
        require(label.isNotBlank()) { "A button needs a label." }
        require(label.length <= MAX_LABEL) { "A button label is at most $MAX_LABEL characters, got ${label.length}." }
    }

    @SculkStable
    public companion object {
        public const val MAX_LABEL: Int = 80
    }
}

/** One choice in a [SelectMenu]. */
@SculkStable
public data class SelectOption(
    public val label: String,
    public val value: String,
    public val description: String? = null,
    public val default: Boolean = false,
    /** A unicode character, or a `name:id` custom emoji. */
    public val emoji: String? = null,
)

@SculkStable
public data class SelectMenu(
    public val id: ComponentId,
    public val options: List<SelectOption>,
    public val placeholder: String? = null,
    public val minChoices: Int = 1,
    public val maxChoices: Int = 1,
    public val enabled: Boolean = true,
) : Interactive {
    init {
        require(options.isNotEmpty()) { "A select menu needs at least one option." }
        require(options.size <= MAX_OPTIONS) { "A select menu holds at most $MAX_OPTIONS options, got ${options.size}." }
        require(options.map { it.value }.toSet().size == options.size) {
            "Select option values must be unique; a duplicate makes the choice ambiguous on submit."
        }
        require(minChoices in 0..options.size) { "minChoices must be between 0 and the option count." }
        require(maxChoices in minChoices..options.size) { "maxChoices must be between minChoices and the option count." }
    }

    @SculkStable
    public companion object {
        public const val MAX_OPTIONS: Int = 25
    }
}

/** What an [EntitySelect] lets someone pick. */
@SculkStable
public enum class EntityKind { User, Role, Channel }

/**
 * A select menu over Discord's own users, roles or channels.
 *
 * Distinct from [SelectMenu] because the options are not yours to enumerate: Discord searches its own
 * member and role lists as the user types. A "which Discord account is this?" flow built from a
 * [SelectMenu] means fetching and paginating a member list into twenty-five fixed options, which is
 * both a lot of requests and wrong the moment somebody joins.
 *
 * The chosen ids arrive in [studio.sculk.discord.interaction.ComponentInteraction.selected].
 */
@SculkStable
public data class EntitySelect(
    public val id: ComponentId,
    public val kinds: Set<EntityKind>,
    public val placeholder: String? = null,
    public val minChoices: Int = 1,
    public val maxChoices: Int = 1,
    public val enabled: Boolean = true,
) : Interactive {
    init {
        require(kinds.isNotEmpty()) { "An entity select needs at least one kind to pick from." }
        require(minChoices in 0..MAX_CHOICES) { "minChoices must be between 0 and $MAX_CHOICES." }
        require(maxChoices in minChoices..MAX_CHOICES) { "maxChoices must be between minChoices and $MAX_CHOICES." }
    }

    @SculkStable
    public companion object {
        public const val MAX_CHOICES: Int = 25
    }
}
