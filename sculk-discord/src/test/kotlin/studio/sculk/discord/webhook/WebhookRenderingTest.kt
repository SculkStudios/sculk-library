package studio.sculk.discord.webhook

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.ComponentId
import studio.sculk.discord.Mentions
import studio.sculk.discord.RoleId
import studio.sculk.discord.UserId
import studio.sculk.discord.message.ButtonStyle
import studio.sculk.discord.message.message
import studio.sculk.text.ThemeStyle

class WebhookRenderingTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `every payload carries an empty parse list, so an at-everyone in the body stays inert`() {
        val payload = payloadFor(message { text("@everyone hi") }, null, null)

        assertEquals(emptyList<String>(), payload.allowedMentions.parse)
        assertTrue(json.encodeToString(payload).contains(Char(34) + "parse" + Char(34) + ":[]"))
    }

    @Test
    fun `allowing one user names that user and still does not parse everyone`() {
        val payload = payloadFor(
            message {
                text("hi")
                mentions = Mentions.user(UserId("42"))
            },
            null,
            null,
        )

        assertEquals(listOf("42"), payload.allowedMentions.users)
        assertEquals(emptyList<String>(), payload.allowedMentions.parse)
    }

    @Test
    fun `everyone is opt-in and separate from naming roles`() {
        val allow = Mentions.Allow(roles = setOf(RoleId("7")), everyone = true)

        val rendered = allowedMentionsFor(allow)

        assertEquals(listOf("everyone"), rendered.parse)
        assertEquals(listOf("7"), rendered.roles)
    }

    @Test
    fun `control characters are escaped by the serializer rather than by hand`() {
        // The two implementations this replaces hand-rolled an escaper each, and they disagreed on
        // which control characters to cover — so the same name posted cleanly through one path and
        // produced an HTTP 400 through the other.
        val nasty = "Steve" + Char(1) + Char(34) + Char(92) + Char(10)

        val encoded = json.encodeToString(payloadFor(message { text(nasty) }, null, null))

        assertTrue(encoded.contains("\\u0001"), encoded)
        assertTrue(encoded.contains("\\\""), encoded)
        assertTrue(encoded.contains("\\\\"), encoded)
        assertTrue(encoded.contains("\\n"), encoded)
    }

    @Test
    fun `a container becomes an embed carrying the theme colour`() {
        val payload = payloadFor(
            message {
                container(ThemeStyle.Solid("#e57373")) {
                    text("**Steve** was flagged")
                    divider()
                    text("severity 4")
                }
            },
            username = "DaisyFilter",
            avatarUrl = null,
        )

        val embed = payload.embeds.single()
        assertEquals(0xE57373, embed.color)
        assertEquals("**Steve** was flagged\n───\nseverity 4", embed.description)
        assertNull(payload.content)
        assertEquals("DaisyFilter", payload.username)
    }

    @Test
    fun `loose text becomes the content line rather than an embed`() {
        val payload = payloadFor(message { text("<Steve> hello") }, "Steve", "https://example/avatar.png")

        assertEquals("<Steve> hello", payload.content)
        assertTrue(payload.embeds.isEmpty())
    }

    @Test
    fun `a message with a button is refused, because a webhook has nothing to report the click to`() {
        val withButton = message {
            row { button("Ban", ComponentId.of("punish", "ban", "r1").getOrThrow(), ButtonStyle.Danger) }
        }

        val reason = undeliverableReason(withButton)

        assertNotNull(reason)
        assertTrue(reason!!.contains("gateway"), reason)
    }

    @Test
    fun `a link button is deliverable, since it never produces an interaction`() {
        val withLink = message {
            row { link("Docs", "https://sculk.studio") }
        }

        assertNull(undeliverableReason(withLink))
    }

    @Test
    fun `a username too long for Discord is trimmed rather than rejected`() {
        val payload = payloadFor(message { text("hi") }, "x".repeat(200), null)

        assertEquals(MAX_USERNAME, payload.username!!.length)
    }

    @Test
    fun `a null username is omitted rather than sent as JSON null`() {
        val encoded = json.encodeToString(payloadFor(message { text("hi") }, null, null))

        assertFalse(encoded.contains("username"), encoded)
    }

    @Test
    fun `a link button inside a container survives as a markdown link`() {
        val alert = message {
            container {
                text("Server unreachable")
                row { link("Open incident", "https://status.example/42") }
            }
        }

        val description = payloadFor(alert, null, null).embeds.single().description

        assertTrue(description!!.contains("[Open incident](https://status.example/42)"), description)
    }

    @Test
    fun `a row at the top level is not silently dropped`() {
        val alert = message {
            text("Server unreachable")
            row { link("Open incident", "https://status.example/42") }
        }

        val content = payloadFor(alert, null, null).content

        assertTrue(content!!.contains("https://status.example/42"), content)
    }

    @Test
    fun `a divider inside a container still renders as a rule`() {
        val alert = message {
            container {
                text("before")
                divider()
                text("after")
            }
        }

        assertEquals("before\n───\nafter", payloadFor(alert, null, null).embeds.single().description)
    }
}
