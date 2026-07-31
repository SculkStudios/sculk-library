package studio.sculk.text

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SculkMessagesTest {
    private val theme = SculkTheme(
        mapOf(
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
        ),
    )
    private val messages = SculkMessages(theme)

    private fun plain(component: net.kyori.adventure.text.Component) = PlainTextComponentSerializer.plainText().serialize(component)

    @Test
    fun `a placeholder value containing a minimessage tag renders literally`() {
        val rendered = messages.render("Welcome, <name>.", "name" to "<red>Impostor")

        assertEquals("Welcome, <red>Impostor.", plain(rendered))
    }

    @Test
    fun `a placeholder value containing a click event does not become clickable`() {
        val hostile = "<click:run_command:/op Impostor>free rank</click>"

        val rendered = messages.render("<name> joined.", "name" to hostile)

        assertEquals("$hostile joined.", plain(rendered))
        assertNoClickEvent(rendered)
    }

    @Test
    fun `a placeholder value containing a theme tag is not expanded`() {
        val rendered = messages.render("Item: <item>", "item" to "<danger>Admin Sword")

        assertEquals("Item: <danger>Admin Sword", plain(rendered))
    }

    @Test
    fun `a placeholder whose name collides with a theme style fails loudly`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            messages.render("<danger>", "danger" to "anything")
        }

        assertTrue(failure.message!!.contains("collides"), "the message must say what the problem is: ${failure.message}")
    }

    @Test
    fun `the template itself is still parsed`() {
        val rendered = messages.render("<danger>Denied</danger>")

        assertEquals("Denied", plain(rendered))
        assertEquals(0xff5f5f, rendered.children().firstOrNull()?.color()?.value() ?: rendered.color()?.value())
    }

    @Test
    fun `a template tag that is not a theme style still works`() {
        val rendered = messages.render("<red>plain minimessage</red>")

        assertEquals("plain minimessage", plain(rendered))
        assertTrue(
            rendered.color() == NamedTextColor.RED || rendered.children().any { it.color() == NamedTextColor.RED },
        )
    }

    @Test
    fun `rendered text is non-italic unless the template asks`() {
        val rendered = messages.render("hello")

        assertEquals(
            net.kyori.adventure.text.format.TextDecoration.State.FALSE,
            rendered.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC),
        )
    }

    @Test
    fun `a template that asks for italics keeps them`() {
        val rendered = messages.render("<i>hello</i>")

        assertEquals(
            net.kyori.adventure.text.format.TextDecoration.State.TRUE,
            rendered.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC),
        )
    }

    @Test
    fun `item text is non-italic`() {
        val rendered = messages.renderItemText("<danger>Cursed Blade</danger>")

        assertEquals(
            net.kyori.adventure.text.format.TextDecoration.State.FALSE,
            rendered.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC),
        )
    }

    @Test
    fun `rendering a list applies the same values to every line`() {
        val lines = messages.render(listOf("<name> one", "<name> two"), "name" to "Ada")

        assertEquals(listOf("Ada one", "Ada two"), lines.map { plain(it) })
    }

    private fun assertNoClickEvent(component: net.kyori.adventure.text.Component) {
        assertNull(component.clickEvent(), "no click event may survive an unparsed placeholder")
        component.children().forEach { assertNoClickEvent(it) }
    }

    @Test
    fun `a legitimate click event in the template is preserved`() {
        val rendered = messages.render("<click:run_command:/spawn>go</click>")

        assertEquals(ClickEvent.Action.RUN_COMMAND, rendered.clickEvent()?.action())
    }
}
