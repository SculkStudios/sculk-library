package studio.sculk.example.bot

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.discord.ChannelId
import studio.sculk.discord.DiscordChatMessage
import studio.sculk.discord.FakeDiscordGateway
import studio.sculk.discord.GuildId
import studio.sculk.discord.Mentions
import studio.sculk.discord.MessageId
import studio.sculk.discord.UserId
import studio.sculk.discord.interaction.DiscordActor
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.message.Text
import java.util.logging.Logger

/**
 * The whole bot, tested with no token and no network.
 *
 * This is the claim the framework makes about testability, exercised on the example that demonstrates
 * it. If this file needed a live gateway, the claim would be false.
 */
class BotTest {
    private val gateway = FakeDiscordGateway()
    private val router = InteractionRouter(Logger.getLogger("test"))
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
    fun `the bot registers the commands it advertises`() {
        registerCommands(router, gateway)

        assertEquals(listOf("confirm", "kit", "ping", "punish"), router.registered.map { it.name })
    }

    @Test
    fun `every advertised subcommand resolves`() {
        registerCommands(router, gateway)

        assertTrue(router.resolve("kit list") != null)
        assertTrue(router.resolve("kit give") != null)
        assertTrue(router.resolve("ping") != null)
    }

    @Test
    fun `the relay echoes a human`() = runTest {
        gateway.connect()
        registerRelay(gateway)

        gateway.deliver(chat("!echo hello"))

        assertEquals("**Daisy** said: hello", (gateway.lastSent!!.components.single() as Text).markdown)
    }

    @Test
    fun `the relay pings nothing, even when someone types at-everyone`() = runTest {
        gateway.connect()
        registerRelay(gateway)

        gateway.deliver(chat("!echo @everyone get in here"))

        assertEquals(Mentions.None, gateway.lastSent!!.mentions)
    }

    @Test
    fun `the relay never answers the bot's own message`() = runTest {
        // The echo loop, caught before it can exist.
        gateway.connect()
        registerRelay(gateway)

        gateway.deliver(chat("!echo loop", fromBot = true))

        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `a message that is not a command is left alone`() = runTest {
        gateway.connect()
        registerRelay(gateway)

        gateway.deliver(chat("just chatting"))

        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `the echo is acknowledged with a reaction`() = runTest {
        gateway.connect()
        registerRelay(gateway)

        gateway.deliver(chat("!echo hi"))

        assertEquals(1, gateway.reactions.size)
        assertEquals("✅", gateway.reactions.single().third)
    }
}
