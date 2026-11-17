package studio.sculk.example.staff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class StaffTest {
    private val staff = Staff()
    private val player = UUID.randomUUID()

    @Test
    fun `freezing toggles and reports whether the player is now frozen`() {
        assertTrue(staff.toggleFreeze(player))
        assertTrue(staff.isFrozen(player))
        assertFalse(staff.toggleFreeze(player))
        assertFalse(staff.isFrozen(player))
    }

    @Test
    fun `notes are kept per player in the order they were written`() {
        staff.note(player, Note("Daisy", "Warned for spam"))
        staff.note(player, Note("Alex", "Apologised"))
        assertEquals(listOf("Warned for spam", "Apologised"), staff.notes(player).map { it.text })
        assertEquals(emptyList<Note>(), staff.notes(UUID.randomUUID()))
    }
}
