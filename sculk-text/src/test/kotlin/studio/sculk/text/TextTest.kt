package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
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
        val rendered = Text(Theme.of("loud" to style)).render("<loud>hey</loud>")
        val hey = rendered.flatten().first { plain.serialize(it) == "hey" && it.hasDecoration(TextDecoration.ITALIC) }
        assertEquals(true, hey.hasDecoration(TextDecoration.BOLD) || rendered.flatten().any { it.hasDecoration(TextDecoration.BOLD) })
    }

    @Test
    fun `unknown tags in a template are left for MiniMessage`() {
        assertEquals("a b", plain.serialize(text.render("a <bold>b</bold>")))
    }
}
