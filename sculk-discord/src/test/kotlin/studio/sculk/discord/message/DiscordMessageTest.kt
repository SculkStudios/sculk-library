package studio.sculk.discord.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.ComponentId
import studio.sculk.text.ThemeStyle

class DiscordMessageTest {
    private val ban = ComponentId.of("punish", "ban", "r1").getOrThrow()
    private val reveal = ComponentId.of("punish", "reveal", "r1").getOrThrow()

    @Test
    fun `a message reports the components it actually offers`() {
        val alert = message {
            container(ThemeStyle.Solid("#e57373")) {
                text("**Steve** was flagged")
                divider()
                row {
                    button("Ban", ban, ButtonStyle.Danger)
                    button("Reveal", reveal)
                }
            }
        }

        assertEquals(listOf(ban, reveal), alert.componentIds)
    }

    @Test
    fun `a container takes its accent from the theme style rather than a copied hex table`() {
        val alert = message {
            container(ThemeStyle.Solid("#e57373")) { text("hi") }
        }

        assertEquals(0xE57373, (alert.components.single() as Container).accentRgb)
    }

    @Test
    fun `a gradient style contributes its swatch, so a themed container still has one colour`() {
        val gradient = ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b"))
        val alert = message {
            container(gradient) { text("hi") }
        }

        assertEquals(rgbOf(gradient.swatchHex), (alert.components.single() as Container).accentRgb)
    }

    @Test
    fun `a row of six components is refused when it is built, not by Discord on send`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            message {
                row { repeat(6) { i -> button("b$i", ComponentId.of("n", "b$i").getOrThrow()) } }
            }
        }

        assertTrue(error.message!!.contains("5"), "the message should name the limit: ${error.message}")
    }

    @Test
    fun `a button cannot be both a link and an interaction`() {
        assertThrows(IllegalArgumentException::class.java) {
            Button(label = "Both", id = ban, link = "https://example.com")
        }
    }

    @Test
    fun `a button that is neither a link nor an interaction is refused`() {
        assertThrows(IllegalArgumentException::class.java) { Button(label = "Inert") }
    }

    @Test
    fun `a link button carries no component id, so it never appears as an interaction`() {
        val alert = message {
            row { link("Docs", "https://sculk.studio") }
        }

        assertTrue(alert.componentIds.isEmpty())
    }

    @Test
    fun `duplicate select option values are refused because the submitted choice would be ambiguous`() {
        assertThrows(IllegalArgumentException::class.java) {
            SelectMenu(
                id = ban,
                options = listOf(SelectOption("A", "same"), SelectOption("B", "same")),
            )
        }
    }

    @Test
    fun `flatten walks into containers and rows`() {
        val alert = message {
            container {
                text("a")
                row { button("b", ban) }
            }
        }

        // container, text, row, button
        assertEquals(4, alert.flatten().size)
    }

    @Test
    fun `an empty message is refused rather than posted as a blank line`() {
        assertThrows(IllegalArgumentException::class.java) { message { } }
    }
}
