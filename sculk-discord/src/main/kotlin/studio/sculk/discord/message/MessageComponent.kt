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
) : MessageComponent {
    init {
        require(children.isNotEmpty()) { "A container with no children renders as an empty block." }
        require(accentRgb == null || accentRgb in 0x000000..0xFFFFFF) {
            "Accent must be a 24-bit RGB value, got ${accentRgb?.toString(16)}."
        }
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
) : Interactive {
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
