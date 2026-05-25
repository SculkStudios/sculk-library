package studio.sculk.packets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PacketBackendModeTest {
    @Test
    fun `auto backend mode remains first class default`() {
        assertEquals(PacketBackendMode.Auto, PacketServiceConfig().backend)
    }

    @Test
    fun `packet events is preferred in backend ordering`() {
        assertEquals(PacketBackend.PacketEvents, PacketBackend.entries.first())
    }
}
