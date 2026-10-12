package studio.sculk.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.SculkResult

class CapabilitiesTest {
    private val dialogs = CapabilityKey<String>("paper.dialogs", "Dialogs")
    private val postEffects = CapabilityKey<String>("paper.post-effects", "Player post-processing effects")

    @Test
    fun `a key nobody provides is unavailable rather than missing`() {
        val capability = Capabilities.NONE[dialogs]
        assertFalse(capability.isAvailable)
        assertNull(capability.service)
        assertEquals("unavailable", capability.level)
    }

    @Test
    fun `asking for a missing service explains which feature is missing and why`() {
        val capabilities = Capabilities.Builder().resolve(postEffects, Capability.Unavailable("added in Minecraft 26.3")).build()
        val result = capabilities.service(postEffects) as SculkFailure.UnsupportedCapability
        assertEquals("Player post-processing effects is not available on this server: added in Minecraft 26.3.", result.message)
    }

    @Test
    fun `every available level hands out its service`() {
        val capabilities = Capabilities.Builder()
            .resolve(dialogs, Capability.Degraded("inventory form", listOf("text input")))
            .build()
        assertEquals(SculkResult.Success("inventory form"), capabilities.service(dialogs))
        assertTrue(capabilities[dialogs].isAvailable)
        assertEquals("degraded", capabilities[dialogs].level)
    }

    @Test
    fun `a capability is resolved by exactly one adapter`() {
        val builder = Capabilities.Builder().resolve(dialogs, Capability.Native("native"))
        assertThrows<IllegalStateException> { builder.resolve(dialogs, Capability.Native("other")) }
    }

    @Test
    fun `two keys sharing an id cannot both be resolved, so a lookup never returns another key's type`() {
        val impostor = CapabilityKey<Int>("paper.dialogs", "Dialogs")
        val builder = Capabilities.Builder().resolve(dialogs, Capability.Native("native"))
        assertThrows<IllegalStateException> { builder.resolve(impostor, Capability.Native(1)) }
        assertFalse(builder.build()[impostor].isAvailable)
    }

    @Test
    fun `capability ids are validated`() {
        assertThrows<IllegalArgumentException> { CapabilityKey<String>("Paper Dialogs", "x") }
    }

    @Test
    fun `diagnostics list capabilities ordered by id`() {
        val capabilities = Capabilities.Builder()
            .resolve(postEffects, Capability.Unavailable("x"))
            .resolve(dialogs, Capability.Native("y"))
            .build()
        assertEquals(listOf("paper.dialogs", "paper.post-effects"), capabilities.all().map { it.first.id })
    }
}
