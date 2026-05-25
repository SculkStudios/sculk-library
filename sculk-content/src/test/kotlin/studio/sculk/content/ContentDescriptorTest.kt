package studio.sculk.content

import org.bukkit.entity.EntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentDescriptorTest {
    @Test
    fun `fake entity descriptor captures builder state`() {
        val descriptor =
            FakeEntityDescriptorBuilder()
                .apply {
                    type(EntityType.ARMOR_STAND)
                    invisible()
                    marker()
                    name("<aqua>Click me")
                }.build()

        assertEquals(EntityType.ARMOR_STAND, descriptor.type)
        assertTrue(descriptor.invisible)
        assertTrue(descriptor.marker)
        assertEquals("<aqua>Click me", descriptor.name)
    }

    @Test
    fun `nametag descriptor keeps optional parts nullable`() {
        val descriptor =
            NametagDescriptorBuilder()
                .apply {
                    prefix("<red>Admin ")
                }.build()

        assertEquals("<red>Admin ", descriptor.prefix)
        assertNull(descriptor.suffix)
    }
}
