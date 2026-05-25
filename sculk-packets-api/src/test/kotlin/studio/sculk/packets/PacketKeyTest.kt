package studio.sculk.packets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PacketKeyTest {
    @Test
    fun `normalizes bare packet keys`() {
        assertEquals(PacketKey(value = "use_entity"), PacketKey.of(" USE ENTITY "))
    }

    @Test
    fun `normalizes namespaced packet keys`() {
        assertEquals(PacketKey("minecraft", "entity_metadata"), PacketKey.of("minecraft:ENTITY-METADATA"))
    }
}
