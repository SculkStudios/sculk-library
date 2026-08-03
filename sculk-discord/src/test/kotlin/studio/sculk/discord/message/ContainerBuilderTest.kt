package studio.sculk.discord.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.discord.Mentions
import studio.sculk.discord.UserId

class ContainerBuilderTest {
    @Test
    fun `mentions set on the message survive a container`() {
        // Previously `container { }` reused MessageBuilder, so setting mentions inside one compiled,
        // wrote to a throwaway and was discarded — and inside a container is exactly where someone
        // reaches for it, because that is where the rest of a real message is written.
        val alert = message {
            mentions = Mentions.user(UserId("42"))
            container { text("hi") }
        }

        assertEquals(Mentions.user(UserId("42")), alert.mentions)
    }

    @Test
    fun `ephemeral set on the message survives a container`() {
        val alert = message {
            ephemeral = true
            container { text("hi") }
        }

        assertEquals(true, alert.ephemeral)
    }

    @Test
    fun `a container still nests rows and dividers`() {
        val alert = message {
            container {
                text("a")
                divider()
                row { link("Docs", "https://sculk.studio") }
            }
        }

        val container = alert.components.single() as Container
        assertEquals(3, container.children.size)
    }
}
