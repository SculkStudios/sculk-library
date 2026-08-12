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
        gateway.onMessage { names = it.attachments.map { attachment -> attachment.fileName } }

        gateway.deliver(
            chat("look").copy(
                attachments = listOf(
                    DiscordAttachment("proof.png", "https://cdn.example/proof.png"),
                    DiscordAttachment("log.txt", "https://cdn.example/log.txt"),
                ),
            ),
        )

        assertEquals(listOf("proof.png", "log.txt"), names)
    }

    @Test
    fun `an attachment knows whether it is an image, so a relay can say which`() {
        val image = DiscordAttachment("proof.png", "https://cdn.example/proof.png", contentType = "image/png")
        val text = DiscordAttachment("log.txt", "https://cdn.example/log.txt", contentType = "text/plain")
        val unknown = DiscordAttachment("blob", "https://cdn.example/blob")

        assertEquals(listOf(true, false, false), listOf(image.isImage, text.isImage, unknown.isImage))
    }

    @Test
    fun `display content falls back to the raw content when nothing resolved it`() {
        val message = chat("hello <@123>")

        assertEquals("hello <@123>", message.displayContent)
    }

    @Test
    fun `a reply carries who was answered, so the relay is not a non-sequitur`() = runTest {
        var reply: ReplyContext? = null
        gateway.onMessage { reply = it.reply }

        val quoted = ReplyContext(MessageId("55"), DiscordActor(UserId("7"), "Ash", null), "is the server up?")
        gateway.deliver(chat("yes").copy(reply = quoted))

        assertEquals("Ash", reply?.author?.name)
        assertEquals("is the server up?", reply?.excerpt)
    }
}
