package studio.sculk.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudRulesTest {
    @Test
    fun `a signature changes when a resolved value changes even though the template did not`() {
        // The classic sidebar bug. "Balance: <coins>" never changes as a template, so comparing
        // templates draws the row once and freezes the number for the rest of the session.
        val before = sidebarSignature("Balance: <coins>", listOf("100"))
        val after = sidebarSignature("Balance: <coins>", listOf("250"))

        assertNotEquals(before, after)
    }

    @Test
    fun `an identical row produces an identical signature`() {
        assertEquals(
            sidebarSignature("Balance: <coins>", listOf("100")),
            sidebarSignature("Balance: <coins>", listOf("100")),
        )
    }

    @Test
    fun `a row with no values signs as its template`() {
        assertEquals("static row", sidebarSignature("static row", emptyList()))
    }

    @Test
    fun `a changed row is redrawn`() {
        assertTrue(needsRedraw(changed = true, moved = false, centred = false, widestChanged = false))
    }

    @Test
    fun `an unchanged row is not redrawn`() {
        assertFalse(needsRedraw(changed = false, moved = false, centred = false, widestChanged = false))
    }

    @Test
    fun `a still centred row is redrawn when the widest row moved`() {
        // Centring is measured against the widest row, so an untouched row drifts out of
        // alignment when a neighbour grows. This is the rule that is easiest to leave out and
        // hardest to notice.
        assertTrue(needsRedraw(changed = false, moved = false, centred = true, widestChanged = true))
    }

    @Test
    fun `a still left-aligned row is not redrawn when the widest row moved`() {
        assertFalse(needsRedraw(changed = false, moved = false, centred = false, widestChanged = true))
    }

    @Test
    fun `every row is redrawn when the row count changed`() {
        assertTrue(needsRedraw(changed = false, moved = true, centred = false, widestChanged = false))
    }

    @Test
    fun `the first row gets the highest score so it draws at the top`() {
        // Minecraft draws a sidebar in descending score order.
        assertEquals(3, scoreFor(index = 0, total = 3))
        assertEquals(2, scoreFor(index = 1, total = 3))
        assertEquals(1, scoreFor(index = 2, total = 3))
    }

    @Test
    fun `row entries are unique across the whole sidebar`() {
        val entries = (0 until MAX_SIDEBAR_LINES).map { rowEntry(it) }

        assertEquals(entries.size, entries.toSet().size, "a repeated entry would collapse two rows into one")
    }

    @Test
    fun `the line cap stays within the number of distinct entries available`() {
        assertTrue(MAX_SIDEBAR_LINES <= 16, "there are only sixteen colour codes to build entries from")
    }
}
