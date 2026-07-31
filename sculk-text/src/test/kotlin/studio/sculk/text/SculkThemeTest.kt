package studio.sculk.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SculkThemeTest {
    private val theme = SculkTheme(
        mapOf(
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
        ),
    )

    @Test
    fun `expand rewrites both the opening and closing tag`() {
        assertEquals("<color:#ff5f5f>no</color>", theme.expand("<danger>no</danger>"))
    }

    @Test
    fun `a gradient expands to a scoped gradient tag`() {
        assertEquals("<gradient:#8be9fd:#50fa7b>42</gradient>", theme.expand("<value>42</value>"))
    }

    @Test
    fun `an unknown style name is left alone for minimessage to handle`() {
        assertEquals("<red>hi</red>", theme.expand("<red>hi</red>"))
    }

    @Test
    fun `expanding the same template twice returns the same result`() {
        val template = "<danger>x</danger>"

        assertEquals(theme.expand(template), theme.expand(template))
    }

    @Test
    fun `with layers overrides without touching the original`() {
        val seasonal = theme.with(mapOf("danger" to ThemeStyle.Solid("#000000")))

        assertEquals("<color:#000000>", seasonal.expand("<danger>"))
        assertEquals("<color:#ff5f5f>", theme.expand("<danger>"), "the original theme must be unchanged")
    }

    @Test
    fun `the empty theme leaves everything alone`() {
        assertEquals("<danger>x</danger>", SculkTheme.EMPTY.expand("<danger>x</danger>"))
    }

    @Test
    fun `a gradient swatch is a middle stop rather than an end`() {
        assertEquals("#50fa7b", ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")).swatchHex)
        assertEquals("#ffffff", ThemeStyle.Gradient(listOf("#000000", "#ffffff", "#111111")).swatchHex)
    }

    @Test
    fun `a malformed colour is rejected at construction rather than at render time`() {
        assertThrows(IllegalArgumentException::class.java) { ThemeStyle.Solid("ff5f5f") }
        assertThrows(IllegalArgumentException::class.java) { ThemeStyle.Solid("#ff5f5") }
        assertThrows(IllegalArgumentException::class.java) { ThemeStyle.Gradient(listOf("#ff5f5f")) }
    }

    @Test
    fun `a shader style must name the effect it stands for`() {
        assertThrows(IllegalArgumentException::class.java) { ThemeStyle.Shader("#ff5fa3", " ") }
        assertEquals("<color:#ff5fa3>", ThemeStyle.Shader("#ff5fa3", "pulse").open)
    }
}
