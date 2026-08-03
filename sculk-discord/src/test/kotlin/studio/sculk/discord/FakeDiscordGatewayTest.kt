package studio.sculk.discord

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.discord.message.message

class FakeDiscordGatewayTest {
    private val gateway = FakeDiscordGateway()
    private val channel = ChannelId("1035829461209384960")

    @Test
    fun `sending before connecting fails the way a real gateway does`() = runTest {
        // A fake more forgiving than production proves nothing: this is the exact race a caller hits
        // when it sends immediately after a connect it did not await.
        val result = gateway.send(channel, message { text("hi") })

        assertTrue(result.isFailure)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `a sent message is recorded with its channel`() = runTest {
        gateway.connect()

        gateway.send(channel, message { text("hello") })

        val sent = gateway.sent.single()
        assertEquals(channel, sent.channel)
        assertEquals("hello", (sent.message.components.single() as studio.sculk.discord.message.Text).markdown)
    }

    @Test
    fun `the mention policy survives onto the recorded message, so a test can assert it`() = runTest {
        gateway.connect()

        gateway.send(channel, message { text("@everyone") })

        assertEquals(Mentions.None, gateway.lastSent!!.mentions)
    }

    @Test
    fun `a set failure makes every call fail, for testing the fallback path`() = runTest {
        gateway.connect()
        gateway.failure = "the gateway is reconnecting"

        val result = gateway.send(channel, message { text("hi") }) as SculkResult.Failure

        assertEquals("the gateway is reconnecting", result.message)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `a closed gateway refuses rather than silently accepting`() = runTest {
        gateway.connect()
        gateway.close()

        assertTrue(gateway.send(channel, message { text("hi") }).isFailure)
        assertEquals(GatewayState.Disconnected, gateway.state)
    }

    @Test
    fun `channelExists reports only channels the test declared`() = runTest {
        gateway.connect()
        gateway.knownChannels += channel

        assertTrue(gateway.channelExists(channel).getOrThrow())
        assertFalse(gateway.channelExists(ChannelId("999")).getOrThrow())
    }

    @Test
    fun `edits and deletes are recorded separately from sends`() = runTest {
        gateway.connect()
        val id = gateway.send(channel, message { text("first") }).getOrThrow()

        gateway.edit(channel, id, message { text("second") })
        gateway.delete(channel, id)

        assertEquals(1, gateway.sent.size)
        assertEquals("second", (gateway.edited.single().message.components.single() as studio.sculk.discord.message.Text).markdown)
        assertEquals(listOf(id), gateway.deleted)
    }
}
