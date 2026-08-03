package studio.sculk.discord

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.interaction.DiscordActor

class DiscordChatMessageTest {
    private val gateway = FakeDiscordGateway()
    private val channel = ChannelId("1035829461209384960")
    private val author = DiscordActor(UserId("2035829461209384961"), "Daisy", GuildId("3035829461209384962"))

    private fun chat(content: String, fromBot: Boolean = false) = DiscordChatMessage(
        id = MessageId("9"),
        channel = channel,
        guild = author.guild,
        author = author,
        content = content,
        fromBot = fromBot,
    )

    @Test
    fun `a handler sees a human's message`() = runTest {
        val seen = mutableListOf<String>()
        gateway.onMessage { seen += it.content }

        gateway.deliver(chat("hello from discord"))

        assertEquals(listOf("hello from discord"), seen)
    }

    @Test
    fun `a bot's own message never reaches a handler`() = runTest {
        // A relay that reacts to what it just posted is an infinite loop whose only brake is a rate
        // limit. Every chat bridge writes this filter, so the framework owns it.
        val seen = mutableListOf<String>()
        gateway.onMessage { seen += it.content }

        gateway.deliver(chat("<Steve> hi", fromBot = true))

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `closing the handle stops delivery`() = runTest {
        val seen = mutableListOf<String>()
        val handle = gateway.onMessage { seen += it.content }

        handle.close()
        gateway.deliver(chat("after close"))

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `two handlers both see the message`() = runTest {
        var first = 0
        var second = 0
        gateway.onMessage { first++ }
        gateway.onMessage { second++ }

        gateway.deliver(chat("hi"))

        assertEquals(1, first)
        assertEquals(1, second)
    }

    @Test
    fun `attachments are named, so a relay can say something happened`() = runTest {
        var names = emptyList<String>()
        gateway.onMessage { names = it.attachments }

        gateway.deliver(chat("look").copy(attachments = listOf("proof.png", "log.txt")))

        assertEquals(listOf("proof.png", "log.txt"), names)
    }
}
