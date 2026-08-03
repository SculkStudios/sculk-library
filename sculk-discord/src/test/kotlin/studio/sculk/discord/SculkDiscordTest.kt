package studio.sculk.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.message.message

class SculkDiscordTest {
    private val token = "MTIzNDU2Nzg5MDEyMzQ1Njc4.GaBcDe.notarealtokenatall"

    private class StubProvider(override val backend: String, private val available: Boolean) : DiscordGatewayProvider {
        override fun isAvailable(): Boolean = available

        override fun create(config: BotConfig, scope: CoroutineScope): DiscordGateway = FakeDiscordGateway()
    }

    @Test
    fun `a blank token is reported as unconfigured rather than as a missing backend`() {
        val result = SculkDiscord.createWith(BotConfig(""), CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), emptyList())

        val failure = result as SculkResult.Failure
        assertTrue(failure.message.contains("token"), failure.message)
    }

    @Test
    fun `the placeholder a generated config ships with does not count as configured`() {
        assertFalse(BotConfig("PASTE_BOT_TOKEN").configured)
        assertTrue(BotConfig(token).configured)
    }

    @Test
    fun `no backend on the classpath names the dependency and the shading trap`() {
        val result = SculkDiscord.createWith(BotConfig(token), CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), emptyList())

        val failure = result as SculkResult.Failure
        assertTrue(failure.message.contains("sculk-discord-jda"), failure.message)
        assertTrue(failure.message.contains("META-INF/services"), failure.message)
    }

    @Test
    fun `a backend present but with its library missing is a different message from no backend at all`() {
        val result = SculkDiscord.createWith(
            BotConfig(token),
            CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            listOf(StubProvider("JDA", available = false)),
        )

        val failure = result as SculkResult.Failure
        assertTrue(failure.message.contains("JDA"), failure.message)
        assertTrue(failure.message.contains("libraries:"), failure.message)
    }

    @Test
    fun `the first available backend wins`() {
        val result = SculkDiscord.createWith(
            BotConfig(token),
            CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            listOf(StubProvider("Broken", available = false), StubProvider("JDA", available = true)),
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `a disabled gateway fails by name rather than being null`() = runTest {
        val gateway = SculkDiscord.disabled("no token configured")

        assertEquals(GatewayState.Disabled, gateway.state)
        val failure = gateway.send(ChannelId("1"), message { text("hi") }) as SculkResult.Failure
        assertTrue(failure.message.contains("no token configured"), failure.message)
    }

    @Test
    fun `an intent knows whether Discord treats it as privileged`() {
        assertTrue(Intent.MessageContent.privileged)
        assertTrue(Intent.GuildMembers.privileged)
        assertFalse(Intent.GuildMessages.privileged)
    }

    @Test
    fun `a channel id pasted as a name is caught by the shape check`() {
        assertFalse(ChannelId("#staff-alerts").isWellFormed())
        assertTrue(ChannelId("1035829461209384960").isWellFormed())
    }
}
