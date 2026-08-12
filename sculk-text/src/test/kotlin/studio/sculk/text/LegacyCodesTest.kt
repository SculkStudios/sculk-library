package studio.sculk.text

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Legacy `&` codes have to keep working, because every config written before MiniMessage uses them.
 *
 * The tests that matter most here are the ones about what is *not* converted: a value must never
 * become markup, and a `&` inside a MiniMessage tag argument must not turn into a tag.
 */
class LegacyCodesTest {
    private val messages = SculkMessages()
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `colour and format codes become their tags`() {
        assertEquals("<red>No.", LegacyCodes.toMiniMessage("&cNo."))
        assertEquals("<bold><yellow>Hi", LegacyCodes.toMiniMessage("&l&eHi"))
        assertEquals("<reset>plain", LegacyCodes.toMiniMessage("&rplain"))
        // The section sign is what the server itself emits, so it has to work too.
        assertEquals("<green>ok", LegacyCodes.toMiniMessage("§aok"))
        // Uppercase is what half the configs in the wild actually contain.
        assertEquals("<red>No.", LegacyCodes.toMiniMessage("&CNo."))
    }

    @Test
    fun `both hex spellings are understood`() {
        assertEquals("<#FFC8DD>pink", LegacyCodes.toMiniMessage("&#FFC8DDpink"))
        // Spigot's `&x&f&f&c&8&d&d`, which means exactly the same thing.
        assertEquals("<#FFC8DD>pink", LegacyCodes.toMiniMessage("&x&F&F&C&8&D&Dpink"))
        // Too short to be a hex colour: left alone rather than half-consumed.
        assertEquals("&#FFCpink", LegacyCodes.toMiniMessage("&#FFCpink"))
    }

    @Test
    fun `an ampersand that is not a code is left alone`() {
        assertEquals("Tom & Jerry", LegacyCodes.toMiniMessage("Tom & Jerry"))
        assertEquals("Deaths && Kills", LegacyCodes.toMiniMessage("Deaths && Kills"))
        assertEquals("100% & rising&", LegacyCodes.toMiniMessage("100% & rising&"))
        assertEquals("nothing here", LegacyCodes.toMiniMessage("nothing here"))
    }

    /**
     * A tag's arguments are quoted text MiniMessage parses itself. Substituting inside one produces
     * a tag that never closes, and the whole message then fails to render.
     */
    @Test
    fun `codes inside a MiniMessage tag are not touched`() {
        val template = "<hover:show_text:'Tom &b Jerry'>hi</hover>"

        assertEquals(template, LegacyCodes.toMiniMessage(template))
    }

    @Test
    fun `legacy and MiniMessage mix in one string`() {
        assertEquals("<red>red <green>green</green>", LegacyCodes.toMiniMessage("&cred <green>green</green>"))
    }

    @Test
    fun `templates render through SculkMessages`() {
        val component = messages.render("&cDenied")

        assertEquals("Denied", plain.serialize(component))
        assertEquals(NamedTextColor.RED, component.color())
    }

    @Test
    fun `a hex template renders to that colour`() {
        val component = messages.render("&#FFC8DDpink")

        assertEquals("pink", plain.serialize(component))
        assertEquals(TextColor.fromHexString("#FFC8DD"), component.color())
    }

    @Test
    fun `format codes reach the component`() {
        val component = messages.render("&lLoud")

        assertEquals("Loud", plain.serialize(component))
        assertTrue(component.hasDecoration(TextDecoration.BOLD), "bold must survive the conversion")
    }

    /**
     * The guarantee the whole text layer exists for, extended to legacy codes.
     *
     * A value goes in through `Placeholder.unparsed` *after* conversion, so a player who names
     * themselves `&cImpostor` prints those four characters rather than turning red — the same
     * protection `<red>` in a name already had.
     */
    @Test
    fun `a legacy code inside a value stays literal text`() {
        val component = messages.render("Welcome <player>", "player" to "&cImpostor")

        assertEquals("Welcome &cImpostor", plain.serialize(component))
    }

    @Test
    fun `present reports only what would change`() {
        assertTrue(LegacyCodes.present("&cred"))
        assertTrue(LegacyCodes.present("&#FFC8DD"))
        assertFalse(LegacyCodes.present("Tom & Jerry"))
        assertFalse(LegacyCodes.present("<red>plain</red>"))
        assertFalse(LegacyCodes.present(""))
    }
}
