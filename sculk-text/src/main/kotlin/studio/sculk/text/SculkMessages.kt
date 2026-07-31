package studio.sculk.text

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.title.Title
import studio.sculk.annotation.SculkStable
import java.time.Duration

/**
 * The only place a string becomes a [Component].
 *
 * **Templates are trusted and parsed; values are not and go in via [Placeholder.unparsed].** Never
 * substitute a value into a template before rendering — that is a markup-injection hole, and it is
 * why this type is the single entry point rather than one of several.
 *
 * ```kotlin
 * messages.send(player, "<danger>No room for <value><item></value>.", "item" to stack.displayName())
 * ```
 *
 * See [docs.sculk.studio/text/placeholders](https://docs.sculk.studio/text/placeholders/).
 */
@SculkStable
public class SculkMessages(
    public val theme: SculkTheme = SculkTheme.EMPTY,
    shadowArgb: Int? = null,
    private val suppressItalics: Boolean = true,
    private val miniMessage: MiniMessage = MiniMessage.miniMessage(),
) {
    private val shadow: ShadowColor? = shadowArgb?.let { ShadowColor.shadowColor(it) }

    /** Renders [template], inserting [values] as literal text. */
    @SculkStable
    public fun render(template: String, vararg values: Pair<String, String>): Component = render(template, null, *values)

    /**
     * Renders [template] with an [extra] resolver alongside the placeholder values.
     *
     * For tags that depend on who is looking — an icon glyph for players with the resource pack
     * and a word for those without.
     */
    @SculkStable
    public fun render(template: String, extra: TagResolver?, vararg values: Pair<String, String>): Component {
        val resolvers = ArrayList<TagResolver>(values.size + 1)
        for ((name, value) in values) {
            require(name !in theme.names) {
                "Placeholder '$name' collides with the theme style of the same name; rename the placeholder."
            }
            resolvers += Placeholder.unparsed(name, value)
        }
        extra?.let { resolvers += it }

        val parsed = miniMessage.deserialize(theme.expand(template), TagResolver.resolver(resolvers))
        return applyFallbackStyle(parsed)
    }

    /** Renders each line of [lines] with the same [values]. */
    @SculkStable
    public fun render(lines: List<String>, vararg values: Pair<String, String>): List<Component> = lines.map { render(it, null, *values) }

    @SculkStable
    public fun send(audience: Audience, template: String, vararg values: Pair<String, String>) {
        audience.sendMessage(render(template, null, *values))
    }

    @SculkStable
    public fun send(audience: Audience, lines: List<String>, vararg values: Pair<String, String>) {
        for (line in lines) audience.sendMessage(render(line, null, *values))
    }

    @SculkStable
    public fun actionBar(audience: Audience, template: String, vararg values: Pair<String, String>) {
        audience.sendActionBar(render(template, null, *values))
    }

    @SculkStable
    public fun title(
        audience: Audience,
        title: String,
        subtitle: String = "",
        fadeInTicks: Long = 10,
        stayTicks: Long = 70,
        fadeOutTicks: Long = 20,
        vararg values: Pair<String, String>,
    ) {
        val times = Title.Times.times(
            Duration.ofMillis(fadeInTicks * 50),
            Duration.ofMillis(stayTicks * 50),
            Duration.ofMillis(fadeOutTicks * 50),
        )
        audience.showTitle(Title.title(render(title, null, *values), render(subtitle, null, *values), times))
    }

    /**
     * Renders text destined for an item name or lore.
     *
     * Item text is italic by default in vanilla, which quietly undoes any careful formatting; this
     * turns it off unless the template asked for it.
     */
    @SculkStable
    public fun renderItemText(template: String, vararg values: Pair<String, String>): Component =
        render(template, null, *values).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    private fun applyFallbackStyle(component: Component): Component {
        var styled = component
        // `IfAbsent` throughout: a template that sets its own shadow or asks for italics wins.
        shadow?.let { styled = styled.shadowColorIfAbsent(it) }
        if (suppressItalics) {
            styled = styled.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        }
        return styled
    }
}
