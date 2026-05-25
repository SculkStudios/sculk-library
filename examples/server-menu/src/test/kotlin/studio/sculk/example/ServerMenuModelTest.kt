package studio.sculk.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerMenuModelTest {
    @Test
    fun `main menu slots stay stable`() {
        assertEquals(10, ServerMenuModel.mainSlots["warps"])
        assertEquals(12, ServerMenuModel.mainSlots["profile"])
        assertEquals(14, ServerMenuModel.mainSlots["players"])
        assertEquals(16, ServerMenuModel.mainSlots["settings"])
    }

    @Test
    fun `pagination page count works`() {
        assertEquals(1, ServerMenuModel.pageCount(0, 45))
        assertEquals(1, ServerMenuModel.pageCount(45, 45))
        assertEquals(2, ServerMenuModel.pageCount(46, 45))
    }

    @Test
    fun `empty player list renders fallback path`() {
        assertFalse(ServerMenuModel.hasPlayers(0))
        assertTrue(ServerMenuModel.hasPlayers(1))
    }
}
