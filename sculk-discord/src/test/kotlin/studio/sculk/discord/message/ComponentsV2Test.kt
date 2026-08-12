package studio.sculk.discord.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.ComponentId

class ComponentsV2Test {
    private val pick = ComponentId.of("link", "pick").getOrThrow()
    private val head = Thumbnail("https://crafatar.com/avatars/steve", description = "Steve's head")

    @Test
    fun `a section pairs its text with one accessory`() {
        val relayed = message {
            section(head) { text("<Steve> hello") }
        }

        val section = relayed.components.single() as Section
        assertEquals(head, section.accessory)
        assertEquals(listOf(Text("<Steve> hello")), section.content)
    }

    @Test
    fun `a section with no text is refused, since the accessory alone renders as a stray image`() {
        assertThrows(IllegalArgumentException::class.java) { message { section(head) { } } }
    }

    @Test
    fun `a section is capped at Discord's three lines`() {
        assertThrows(IllegalArgumentException::class.java) {
            message {
                section(head) {
                    repeat(Section.MAX_CONTENT + 1) { text("line") }
                }
            }
        }
    }

    @Test
    fun `a button accessory is reachable from flatten, so a webhook cannot miss it`() {
        val relayed = message {
            section(Button("Ban", pick)) { text("<Steve> hello") }
        }

        assertEquals(listOf(pick), relayed.componentIds)
    }

    @Test
    fun `a gallery holds up to ten images`() {
        val shots = message {
            gallery { repeat(MediaGallery.MAX_ITEMS) { image("https://cdn.example/$it.png") } }
        }

        assertEquals(MediaGallery.MAX_ITEMS, (shots.components.single() as MediaGallery).items.size)
    }

    @Test
    fun `an eleventh image is refused rather than silently dropped by Discord`() {
        assertThrows(IllegalArgumentException::class.java) {
            message { gallery { repeat(MediaGallery.MAX_ITEMS + 1) { image("https://cdn.example/$it.png") } } }
        }
    }

    @Test
    fun `an empty gallery is refused`() {
        assertThrows(IllegalArgumentException::class.java) { message { gallery { } } }
    }

    @Test
    fun `a container can be spoilered`() {
        val hidden = message { container(accentRgb = null, spoiler = true) { text("the answer") } }

        assertTrue((hidden.components.single() as Container).spoiler)
    }

    @Test
    fun `an entity select needs no options, because Discord supplies them`() {
        val prompt = message {
            row { selectEntity(pick, EntityKind.User, placeholder = "Pick your account") }
        }

        val select = (prompt.components.single() as Row).components.single() as EntitySelect
        assertEquals(setOf(EntityKind.User), select.kinds)
        assertEquals(listOf(pick), prompt.componentIds)
    }

    @Test
    fun `an entity select with no kinds is refused`() {
        assertThrows(IllegalArgumentException::class.java) { EntitySelect(pick, emptySet()) }
    }

    @Test
    fun `a thumbnail description is capped at Discord's limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            Thumbnail("https://cdn.example/x.png", description = "x".repeat(Thumbnail.MAX_DESCRIPTION + 1))
        }
    }

    @Test
    fun `a section sits inside a container, which is how a chat block is built`() {
        val block = message {
            container(0x00FF00) {
                section(head) { text("<Steve> hello") }
                divider()
                section(head) { text("<Steve> still here") }
            }
        }

        val container = block.components.single() as Container
        assertEquals(3, container.children.size)
        assertTrue(container.children.first() is Section)
    }
}
