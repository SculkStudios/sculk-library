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
                else -> null
            }
        }

    private fun flattenOne(component: MessageComponent): List<MessageComponent> = when (component) {
        is Container -> listOf(component) + component.children.flatMap { flattenOne(it) }
        is Row -> listOf(component) + component.components
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
    public fun container(style: ThemeStyle?, block: MessageBuilder.() -> Unit) {
        container(style?.swatchHex?.let(::rgbOf), block)
    }

    public fun container(accentRgb: Int? = null, block: MessageBuilder.() -> Unit) {
        components += Container(MessageBuilder().apply(block).components.toList(), accentRgb)
    }

    /** Adds an already-built component, for composing messages across files. */
    public fun add(component: MessageComponent) {
        components += component
    }

    internal fun build(): DiscordMessage = DiscordMessage(components.toList(), mentions, ephemeral)
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

    internal fun build(): Row = Row(components.toList())
}

@SculkStable
public fun message(block: MessageBuilder.() -> Unit): DiscordMessage = MessageBuilder().apply(block).build()

/** `#rrggbb` as a 24-bit int, the form Discord takes an accent colour in. */
@SculkStable
public fun rgbOf(hex: String): Int = hex.removePrefix("#").toInt(16)
