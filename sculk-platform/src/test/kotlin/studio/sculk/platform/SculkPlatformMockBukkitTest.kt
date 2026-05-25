package studio.sculk.platform

import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import studio.sculk.packets.PacketBackendMode

class SculkPlatformMockBukkitTest {
    private lateinit var plugin: JavaPlugin

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `platform builder boots with gui lifecycle and closes idempotently`() {
        val sculk =
            SculkPlatform.create(plugin) {
                gui()
            }

        assertDoesNotThrow {
            sculk.close()
            sculk.close()
        }
    }

    @Test
    fun `required disabled packet backend fails startup clearly`() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                SculkPlatform.create(plugin) {
                    packets {
                        backend = PacketBackendMode.Disabled
                        required = true
                    }
                }
            }

        assertEquals("Packet subsystem is disabled.", error.message)
    }
}
