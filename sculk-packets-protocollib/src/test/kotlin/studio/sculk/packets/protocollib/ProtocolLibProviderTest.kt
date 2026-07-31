package studio.sculk.packets.protocollib

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import studio.sculk.packets.PacketBackend

/** As with the PacketEvents backend, only the parts that do not need a server. */
class ProtocolLibProviderTest {
    private val provider = ProtocolLibPacketServiceProvider()

    @Test
    fun `the provider names its backend`() {
        assertEquals(PacketBackend.ProtocolLib, provider.backend)
    }

    @Test
    fun `it reports unavailable when ProtocolLib is present but not initialised`() {
        assertFalse(provider.isAvailable())
    }
}
