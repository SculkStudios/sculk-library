package studio.sculk.discord.message

import studio.sculk.annotation.SculkStable
import studio.sculk.discord.ComponentId
import studio.sculk.discord.Mentions
import studio.sculk.text.ThemeStyle

/**
 * A message, as data.
 *
 * ```kotlin
 * val alert = message {
 *     container(theme["danger"]) {
 *         text("**$player** was flagged")
 *         divider()
 *         row {
 *             button("Ban", banId, style = ButtonStyle.Danger)
 *             button("Reveal", revealId)
 *         }
 *     }
 * }
 * ```
 *
 * [mentions] defaults to [Mentions.None] and is the whole reason this type carries it rather than
 * leaving it to the send call: a default that has to be remembered at every call site is one that
 * gets forgotten at one of them.
 */
@SculkStable
public data class DiscordMessage(
    public val components: List<MessageComponent>,
    public val mentions: Mentions = Mentions.None,
    /** Visible only to the user who triggered it. Meaningless outside an interaction reply. */
    public val ephemeral: Boolean = false,
) {
    init {
        require(components.isNotEmpty()) { "A message needs at least one component." }
    }

    /** Every component in the tree, containers flattened, in render order. */
    public fun flatten(): List<MessageComponent> = components.flatMap { flattenOne(it) }

    /** Every interactive component's id, so a test can assert what a message actually offers. */
    public val componentIds: List<ComponentId>
        get() = flatten().mapNotNull {
            when (it) {
                is Button -> it.id
                is SelectMenu -> it.id
                is EntitySelect -> it.id
                else -> null
            }
        }

    private fun flattenOne(component: MessageComponent): List<MessageComponent> = when (component) {
        is Container -> listOf(component) + component.children.flatMap { flattenOne(it) }

        is Row -> listOf(component) + component.components

        // The accessory is included because it can be a Button, and a walk that skipped it would let
        // an interactive component reach a webhook unnoticed — which is the one thing flatten() is
        // load-bearing for.
        is Section -> listOf(component) + component.content + listOfNotNull(component.accessory as? MessageComponent)

        else -> listOf(component)
    }
}

/** Builds a [DiscordMessage]. */
@SculkStable
public class MessageBuilder internal constructor() {
    private val components = mutableListOf<MessageComponent>()

    public var mentions: Mentions = Mentions.None
    public var ephemeral: Boolean = false

    public fun text(markdown: String) {
        components += Text(markdown)
    }

    public fun divider(large: Boolean = false) {
        components += Divider(large)
    }

    public fun row(block: RowBuilder.() -> Unit) {
        components += RowBuilder().apply(block).build()
    }

    /** A bordered block accented with [style]'s representative colour. */
    public fun container(style: ThemeStyle?, spoiler: Boolean = false, block: ContainerBuilder.() -> Unit) {
        container(accentRgb = style?.swatchHex?.let(::rgbOf), spoiler = spoiler, block = block)
    }

    public fun container(accentRgb: Int? = null, spoiler: Boolean = false, block: ContainerBuilder.() -> Unit) {
        components += Container(ContainerBuilder().apply(block).contents(), accentRgb, spoiler)
    }

    /** Text with an image or a button beside it. */
    public fun section(accessory: SectionAccessory, block: SectionBuilder.() -> Unit) {
        components += SectionBuilder().apply(block).build(accessory)
    }

    /** A grid of images. */
    public fun gallery(block: GalleryBuilder.() -> Unit) {
        components += GalleryBuilder().apply(block).build()
    }

    /** Adds an already-built component, for composing messages across files. */
    public fun add(component: MessageComponent) {
        components += component
    }

    internal fun build(): DiscordMessage = DiscordMessage(components.toList(), mentions, ephemeral)
}

/**
 * Builds a container's contents.
 *
 * A separate type from [MessageBuilder] purely so that `mentions` and `ephemeral` are **not** in
 * scope here. They are properties of the whole message, and a container is one component of it —
 * when the two shared a builder, setting either inside `container { }` compiled, wrote to a
 * throwaway, and was discarded. Most of a real message is written inside a container, so that is
 * exactly where someone reaches for them.
 */
@SculkStable
public class ContainerBuilder internal constructor() {
    private val components = mutableListOf<MessageComponent>()

    public fun text(markdown: String) {
        components += Text(markdown)
    }

    public fun divider(large: Boolean = false) {
        components += Divider(large)
    }

    public fun row(block: RowBuilder.() -> Unit) {
        components += RowBuilder().apply(block).build()
    }

    /** Text with an image or a button beside it. */
    public fun section(accessory: SectionAccessory, block: SectionBuilder.() -> Unit) {
        components += SectionBuilder().apply(block).build(accessory)
    }

    /** A grid of images. */
    public fun gallery(block: GalleryBuilder.() -> Unit) {
        components += GalleryBuilder().apply(block).build()
    }

    public fun add(component: MessageComponent) {
        components += component
    }

    internal fun contents(): List<MessageComponent> = components.toList()
}

/** Builds one section's lines of text. */
@SculkStable
public class SectionBuilder internal constructor() {
    private val lines = mutableListOf<Text>()

    public fun text(markdown: String) {
        lines += Text(markdown)
    }

    internal fun build(accessory: SectionAccessory): Section = Section(lines.toList(), accessory)
}

/** Builds a media gallery. */
@SculkStable
public class GalleryBuilder internal constructor() {
    private val items = mutableListOf<MediaItem>()

    public fun image(url: String, description: String? = null, spoiler: Boolean = false) {
        items += MediaItem(url, description, spoiler)
    }

    internal fun build(): MediaGallery = MediaGallery(items.toList())
}

/** Builds one action row. */
@SculkStable
public class RowBuilder internal constructor() {
    private val components = mutableListOf<Interactive>()

    public fun button(
        label: String,
        id: ComponentId,
        style: ButtonStyle = ButtonStyle.Secondary,
        enabled: Boolean = true,
        emoji: String? = null,
    ) {
        components += Button(label = label, id = id, style = style, enabled = enabled, emoji = emoji)
    }

    /** A button that navigates instead of reporting a click. */
    public fun link(label: String, url: String, emoji: String? = null) {
        components += Button(label = label, link = url, emoji = emoji)
    }

    public fun select(
        id: ComponentId,
        options: List<SelectOption>,
        placeholder: String? = null,
        minChoices: Int = 1,
        maxChoices: Int = 1,
        enabled: Boolean = true,
    ) {
        components += SelectMenu(id, options, placeholder, minChoices, maxChoices, enabled)
    }

    /**
     * A select over Discord's own users, roles or channels.
     *
     * Discord searches its lists as the user types, so this does not need — and cannot take — options.
     */
    public fun selectEntity(
        id: ComponentId,
        vararg kinds: EntityKind,
        placeholder: String? = null,
        minChoices: Int = 1,
        maxChoices: Int = 1,
        enabled: Boolean = true,
    ) {
        components += EntitySelect(id, kinds.toSet(), placeholder, minChoices, maxChoices, enabled)
    }

    internal fun build(): Row = Row(components.toList())
}

@SculkStable
public fun message(block: MessageBuilder.() -> Unit): DiscordMessage = MessageBuilder().apply(block).build()

/** `#rrggbb` as a 24-bit int, the form Discord takes an accent colour in. */
@SculkStable
public fun rgbOf(hex: String): Int = hex.removePrefix("#").toInt(16)
