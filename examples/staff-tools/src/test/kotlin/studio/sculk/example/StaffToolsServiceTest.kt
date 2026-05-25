package studio.sculk.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class StaffToolsServiceTest {
    private val service = StaffToolsService { 500 }

    @Test
    fun `staff mode toggles on and off`() {
        val uuid = UUID.randomUUID()

        assertTrue(service.toggleStaffMode(uuid).enabled)
        assertFalse(service.toggleStaffMode(uuid).enabled)
    }

    @Test
    fun `freeze state blocks movement decision`() {
        val staff = UUID.randomUUID()
        val target = UUID.randomUUID()

        service.freeze(staff, target, "Testing")

        assertTrue(service.shouldBlockMovement(target))
        assertTrue(service.unfreeze(target))
        assertFalse(service.shouldBlockMovement(target))
    }

    @Test
    fun `staff chat recipient filter respects permission predicate`() {
        val sender = UUID.randomUUID()
        val allowed = UUID.randomUUID()
        val denied = UUID.randomUUID()
        val viewers =
            listOf(
                StaffViewer(sender, "Sender", false),
                StaffViewer(allowed, "Allowed", true),
                StaffViewer(denied, "Denied", false),
            )

        assertEquals(listOf(sender, allowed), service.staffChatRecipients(viewers, sender).map { it.uuid })
    }

    @Test
    fun `quit cleanup removes transient state`() {
        val uuid = UUID.randomUUID()
        service.toggleStaffMode(uuid)
        service.freeze(UUID.randomUUID(), uuid, "Testing")

        service.cleanup(uuid)

        assertFalse(service.shouldBlockMovement(uuid))
    }
}
