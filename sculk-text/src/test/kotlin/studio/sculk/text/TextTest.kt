package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TextTest {
    private val plain = PlainTextComponentSerializer.plainText()
    private val text =
        Text(Theme.of("primary" to ThemeStyle.Solid("#112233"), "fancy" to ThemeStyle.Gradient(listOf("#ff0000", "#0000ff"))))

    private fun Component.flatten(): List<Component> = listOf(this) + children().flatMap { it.flatten() }

    @Test
    fun `markup in a value is printed, not obeyed`() {
        val rendered = text.render("Welcome, <name>!", "name" to "<red><bold>Admin</bold>")
        assertEquals("Welcome, <red><bold>Admin</bold>!", plain.serialize(rendered))
        assertNull(rendered.flatten().firstNotNullOfOrNull { it.color() })
    }

    @Test
    fun `theme styles render where the template uses them`() {
        val rendered = text.render("<primary>Shop</primary> open")
        val shop = rendered.flatten().first { plain.serialize(it).startsWith("Shop") && it.color() != null }
        assertEquals(TextColor.fromHexString("#112233"), shop.color())
        assertEquals("Shop open", plain.serialize(rendered))
    }

    @Test
    fun `a value cannot reach a theme style either`() {
        val rendered = text.render("<name>", "name" to "<primary>fake</primary>")
        assertEquals("<primary>fake</primary>", plain.serialize(rendered))
    }

    @Test
    fun `a component value keeps its own styling`() {
        val badge = Component.text("VIP", NamedTextColor.GOLD)
        val rendered = text.render("Rank: <rank>", "rank" to badge)
        assertEquals(NamedTextColor.GOLD, rendered.flatten().first { plain.serialize(it) == "VIP" }.color())
    }

    @Test
    fun `a null value renders as nothing and numbers render as text`() {
        assertEquals(
            "Balance: 1500 coins",
            plain.serialize(
                text.render(
                    "Balance: <amount><suffix> coins",
                    "amount" to 1500,
                    "suffix" to null,
                ),
            ),
        )
    }

    @Test
    fun `gradients wrap the content they style`() {
        val rendered = text.render("<fancy>ab</fancy>c")
        assertEquals("abc", plain.serialize(rendered))
        val colours = rendered.flatten().mapNotNull { it.color() }.toSet()
        assertEquals(true, colours.size >= 2)
    }

    @Test
    fun `a placeholder named like a theme style is a wiring bug`() {
        assertThrows<IllegalArgumentException> { text.render("<primary>", "primary" to "x") }
    }

    @Test
    fun `theme styles cannot shadow MiniMessage tags or carry invalid colours`() {
        assertThrows<IllegalArgumentException> { Theme.of("red" to ThemeStyle.Solid("#ffffff")) }
        assertThrows<IllegalArgumentException> { ThemeStyle.Solid("red") }
        assertThrows<IllegalArgumentException> { ThemeStyle.Gradient(listOf("#ffffff")) }
    }

    @Test
    fun `decorations open in order and close in reverse`() {
        val style = ThemeStyle.Solid("#abcdef", listOf("bold", "italic"))
        assertEquals("<#abcdef><bold><italic>", style.open)
        assertEquals("</italic></bold></#abcdef>", style.close)
        val rendered = Text(Theme.of("loud" to style)).render("<loud>hey</loud> quiet")
        val styles = rendered.effectiveStyles()
        val hey = styles.getValue("hey")
        assertEquals(TextColor.fromHexString("#abcdef"), hey.color())
        assertEquals(TextDecoration.State.TRUE, hey.decoration(TextDecoration.BOLD))
        assertEquals(TextDecoration.State.TRUE, hey.decoration(TextDecoration.ITALIC))
        val quiet = styles.getValue(" quiet")
        assertNotEquals(TextDecoration.State.TRUE, quiet.decoration(TextDecoration.BOLD))
        assertNull(quiet.color())
    }

    @Test
    fun `theme styles keep their own copy of the caller's lists`() {
        val decorations = mutableListOf("bold")
        val colours = mutableListOf("#000000", "#ffffff")
        val solid = ThemeStyle.Solid("#ffffff", decorations)
        val gradient = ThemeStyle.Gradient(colours)
        decorations += "italic"
        colours.clear()
        assertEquals(listOf("bold"), solid.decorations)
        assertEquals("<#ffffff><bold>", solid.open)
        assertEquals(listOf("#000000", "#ffffff"), gradient.colours)
        assertEquals(ThemeStyle.Solid("#ffffff", listOf("bold")), solid)
    }

    /** The style each text run is drawn with, parents' styles applied. */
    private fun Component.effectiveStyles(inherited: Style = Style.empty()): Map<String, Style> {
        val style = style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
        val own = (this as? TextComponent)?.content()?.takeIf { it.isNotEmpty() }?.let { mapOf(it to style) }.orEmpty()
        return own + children().flatMap { it.effectiveStyles(style).toList() }
    }

    @Test
    fun `unknown tags in a template are left for MiniMessage`() {
        assertEquals("a b", plain.serialize(text.render("a <bold>b</bold>")))
    }
}
