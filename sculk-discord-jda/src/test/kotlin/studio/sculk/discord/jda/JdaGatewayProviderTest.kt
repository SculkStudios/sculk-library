package studio.sculk.discord.jda

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.BotConfig
import studio.sculk.discord.ChannelId
import studio.sculk.discord.GatewayState
import studio.sculk.discord.SculkDiscord
import studio.sculk.discord.message.message

class JdaGatewayProviderTest {
    @Test
    fun `the provider is found through META-INF services`() {
        // The failure this covers is invisible at compile time and looks exactly like a missing
        // dependency at runtime: a shadow configuration that does not merge service descriptors
        // drops this file, and discovery then reports no backend at all.
        val discovered = SculkDiscord.discover(listOf(javaClass.classLoader))

        assertEquals(listOf("JDA"), discovered.map { it.backend })
    }

    @Test
    fun `the provider reports JDA as available when it is on the classpath`() {
        assertTrue(JdaGatewayProvider().isAvailable())
    }

    @Test
    fun `discovery builds a JDA gateway rather than a disabled one`() {
        val result = SculkDiscord.create(
            BotConfig("MTIzNDU2Nzg5MDEyMzQ1Njc4.GaBcDe.notarealtokenatall"),
            CoroutineScope(Dispatchers.Unconfined),
            listOf(javaClass.classLoader),
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() is JdaGateway)
    }

    @Test
    fun `an unconfigured gateway refuses to connect and says why`() = runTest {
        val gateway = JdaGateway(BotConfig(""), this)

        val result = gateway.connect() as SculkResult.Failure

        assertTrue(result.message.contains("token"), result.message)
        assertEquals(GatewayState.Disabled, gateway.state)
    }

    @Test
    fun `sending while disconnected fails by name instead of throwing`() = runTest {
        val gateway = JdaGateway(BotConfig(""), this)

        val result = gateway.send(ChannelId("1"), message { text("hi") }) as SculkResult.Failure

        assertTrue(result.message.contains("Disabled"), result.message)
    }

    @Test
    fun `registering commands before connecting fails rather than silently doing nothing`() = runTest {
        val gateway = JdaGateway(BotConfig(""), this)

        val result = gateway.registerCommands(emptyList()) as SculkResult.Failure

        assertTrue(result.message.contains("not connected"), result.message)
    }
}
