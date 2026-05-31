package studio.sculk.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.items.ItemDescriptor

class CrateServiceTest {
    @Test
    fun `weighted selector ignores invalid zero weights through validation`() {
        val service =
            CrateService({
                CrateSettings(
                    crates =
                        mapOf(
                            "bad" to
                                CrateDefinition(
                                    "<red>Bad",
                                    ItemDescriptor("tripwire_hook"),
                                    listOf(CrateReward("broken", 0, ItemDescriptor("stone"))),
                                ),
                        ),
                )
            })

        assertTrue(service.validate().any { it.contains("positive weight") })
    }

    @Test
    fun `invalid crate id fails clearly`() {
        val service = CrateService({ CrateSettings() })

        assertTrue(service.crate("missing") is SculkResult.Failure)
    }

    @Test
    fun `reward roll uses weights`() {
        val service = CrateService({ CrateSettings() }, random = { 0 })

        val reward = service.roll("vote") as SculkResult.Success

        assertEquals("diamonds", reward.value.id)
    }

    @Test
    fun `key descriptor contains crate id marker`() {
        val service = CrateService({ CrateSettings() })

        val descriptor = service.keyDescriptor("vote") as SculkResult.Success

        assertEquals("vote", descriptor.value.data[CrateService.CRATE_KEY])
        assertFalse(descriptor.value.material.isBlank())
    }
}
