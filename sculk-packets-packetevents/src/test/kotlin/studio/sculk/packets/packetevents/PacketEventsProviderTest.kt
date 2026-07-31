package studio.sculk.packets.packetevents

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import studio.sculk.packets.PacketBackend

/**
 * What can be asserted about this backend without a server.
 *
 * The listener wiring itself cannot be: registering one needs PacketEvents' initialised API, which
 * needs a running server. The behaviour that used to kick players lives in
 * `studio.sculk.packets.PacketGuard` precisely so it *is* testable — see `PacketGuardTest`.
 */
class PacketEventsProviderTest {
    private val provider = PacketEventsPacketServiceProvider()

    @Test
    fun `the provider names its backend`() {
        assertEquals(PacketBackend.PacketEvents, provider.backend)
    }

    @Test
    fun `it reports unavailable when PacketEvents is not initialised`() {
        // PacketEvents is compileOnly and on the test classpath, but never bootstrapped, so the
        // API instance is absent. Reporting false here is what lets the platform degrade to a
        // disabled packet service instead of refusing to enable the plugin.
        assertFalse(provider.isAvailable())
    }

    @Test
    fun `discovery does not throw when the backend is absent`() {
        // The failure mode this guards is a ServiceLoader that throws during class init and takes
        // plugin startup with it, rather than simply reporting no backend.
        repeat(3) { assertFalse(provider.isAvailable()) }
    }
}
