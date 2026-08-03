package studio.sculk.discord.jda

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.entities.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.ComponentId
import studio.sculk.discord.Mentions
import studio.sculk.discord.RoleId
import studio.sculk.discord.UserId
import studio.sculk.discord.message.ButtonStyle
import studio.sculk.discord.message.message
import studio.sculk.text.ThemeStyle
import java.awt.Color

class JdaRenderingTest {
    private val ban = ComponentId.of("punish", "ban", "r1").getOrThrow()

    @Test
    fun `a message with no mention policy allows no mention types`() {
        val data = message { text("@everyone") }.toCreateData()

        assertTrue(data.allowedMentions.isEmpty())
    }

    @Test
    fun `allowing a user permits the user type and nothing else`() {
        val data = message {
            text("hi")
            mentions = Mentions.user(UserId("42"))
        }.toCreateData()

        assertEquals(listOf(Message.MentionType.USER), data.allowedMentions.toList())
        assertEquals(listOf("42"), data.mentionedUsers.toList())
    }

    @Test
    fun `everyone is only permitted when it was asked for explicitly`() {
        val guarded = Mentions.Allow(roles = setOf(RoleId("7"))).toParseTypes()
        val opted = Mentions.Allow(roles = setOf(RoleId("7")), everyone = true).toParseTypes()

        assertFalse(Message.MentionType.EVERYONE in guarded)
        assertTrue(Message.MentionType.EVERYONE in opted)
    }

    @Test
    fun `a themed container renders as a container carrying that colour`() {
        val components = message {
            container(ThemeStyle.Solid("#e57373")) {
                text("**Steve** was flagged")
                divider()
                row { button("Ban", ban, ButtonStyle.Danger) }
            }
        }.toTopLevelComponents()

        val container = components.single() as Container
        assertEquals(Color(0xE57373), container.accentColor)
        assertEquals(3, container.components.size)
    }

    @Test
    fun `a button carries its component id, so the click can be routed back`() {
        val components = message { row { button("Ban", ban, ButtonStyle.Danger) } }.toTopLevelComponents()

        val button = (components.single() as ActionRow).buttons.single()
        assertEquals(ban.encoded, button.customId)
        assertEquals(net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER, button.style)
    }

    @Test
    fun `a link button carries a url and no custom id`() {
        val components = message { row { link("Docs", "https://sculk.studio") } }.toTopLevelComponents()

        val button = (components.single() as ActionRow).buttons.single()
        assertEquals("https://sculk.studio", button.url)
        assertEquals(null, button.customId)
    }

    @Test
    fun `a disabled button renders disabled`() {
        val components = message { row { button("Ban", ban, enabled = false) } }.toTopLevelComponents()

        assertTrue((components.single() as ActionRow).buttons.single().isDisabled)
    }

    @Test
    fun `loose text renders as a text display rather than an embed`() {
        val components = message { text("plain") }.toTopLevelComponents()

        assertEquals("plain", (components.single() as TextDisplay).content)
    }
}
