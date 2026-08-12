package studio.sculk.discord.jda

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.mediagallery.MediaGallery
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.StringSelectMenu
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
import studio.sculk.discord.message.EntityKind
import studio.sculk.discord.message.SelectOption
import studio.sculk.discord.message.Thumbnail
import studio.sculk.discord.message.message
import studio.sculk.text.ThemeStyle
import java.awt.Color
import net.dv8tion.jda.api.components.thumbnail.Thumbnail as JdaThumbnail

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

    @Test
    fun `a section renders with its thumbnail accessory`() {
        val components = message {
            section(Thumbnail("https://crafatar.com/avatars/steve", description = "Steve")) { text("<Steve> hello") }
        }.toTopLevelComponents()

        val section = components.single() as Section
        val thumbnail = section.accessory as JdaThumbnail
        assertEquals("https://crafatar.com/avatars/steve", thumbnail.url)
        assertEquals("Steve", thumbnail.description)
        assertEquals("<Steve> hello", (section.contentComponents.single() as TextDisplay).content)
    }

    @Test
    fun `a section renders with a button accessory`() {
        val components = message {
            section(studio.sculk.discord.message.Button("Ban", ban, style = ButtonStyle.Danger)) { text("flagged") }
        }.toTopLevelComponents()

        val accessory = (components.single() as Section).accessory
        assertEquals(ban.encoded, (accessory as net.dv8tion.jda.api.components.buttons.Button).customId)
    }

    @Test
    fun `a gallery renders every image`() {
        val components = message {
            gallery {
                image("https://cdn.example/one.png", description = "one")
                image("https://cdn.example/two.png", spoiler = true)
            }
        }.toTopLevelComponents()

        val gallery = components.single() as MediaGallery
        assertEquals(listOf("https://cdn.example/one.png", "https://cdn.example/two.png"), gallery.items.map { it.url })
        assertEquals("one", gallery.items.first().description)
        assertTrue(gallery.items.last().isSpoiler)
    }

    @Test
    fun `a spoilered container renders spoilered`() {
        val components = message { container(accentRgb = null, spoiler = true) { text("hidden") } }.toTopLevelComponents()

        assertTrue((components.single() as Container).isSpoiler)
    }

    @Test
    fun `an entity select renders with the kinds it was given`() {
        val components = message {
            row { selectEntity(ban, EntityKind.User, EntityKind.Role, placeholder = "Pick") }
        }.toTopLevelComponents()

        val select = (components.single() as ActionRow).components.single() as EntitySelectMenu
        assertEquals(
            setOf(EntitySelectMenu.SelectTarget.USER, EntitySelectMenu.SelectTarget.ROLE),
            select.entityTypes.toSet(),
        )
    }

    @Test
    fun `a select option keeps its emoji`() {
        val components = message {
            row {
                select(ban, listOf(SelectOption("Green", "green", emoji = "🟢")))
            }
        }.toTopLevelComponents()

        val menu = (components.single() as ActionRow).components.single() as StringSelectMenu
        assertEquals("🟢", menu.options.single().emoji?.formatted)
    }
}
