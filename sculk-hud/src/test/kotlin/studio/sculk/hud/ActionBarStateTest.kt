package studio.sculk.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import java.util.UUID

@OptIn(SculkInternal::class)
class ActionBarStateTest {
    private val state = ActionBarState()
    private val player = UUID.randomUUID()

    private fun row(text: String) = HudRow(text)

    @Test
    fun `an alert preempts activity and expires back to it`() {
        // Without arbitration the most frequent writer wins, so routine feedback buries a warning.
        state.show(player, row("mining"), ActionBarPriority.ACTIVITY, durationTicks = 100, currentTick = 0)
        state.show(player, row("claim expiring"), ActionBarPriority.ALERT, durationTicks = 20, currentTick = 0)

        assertEquals("claim expiring", state.current(player, 5)?.template)

        assertEquals("mining", state.current(player, 25)?.template, "the alert expired, the activity is still live")
    }

    @Test
    fun `feedback does not displace an activity`() {
        state.show(player, row("mining"), ActionBarPriority.ACTIVITY, 100, 0)
        state.show(player, row("+5 coins"), ActionBarPriority.FEEDBACK, 100, 0)

        assertEquals("mining", state.current(player, 1)?.template)
    }

    @Test
    fun `a message of the same priority replaces rather than queues`() {
        state.show(player, row("50%"), ActionBarPriority.ACTIVITY, 100, 0)
        state.show(player, row("75%"), ActionBarPriority.ACTIVITY, 100, 0)

        assertEquals("75%", state.current(player, 1)?.template)

        // If it had queued, the older one would surface once the newer expired.
        assertNull(state.current(player, 200))
    }

    @Test
    fun `a message expires without anyone clearing it`() {
        // A system that sets an action bar and then crashes would otherwise pin its text forever.
        state.show(player, row("temporary"), ActionBarPriority.FEEDBACK, durationTicks = 40, currentTick = 0)

        assertEquals("temporary", state.current(player, 39)?.template)
        assertNull(state.current(player, 40))
    }

    @Test
    fun `an empty state reports nothing`() {
        assertNull(state.current(player, 0))
    }

    @Test
    fun `clearing one priority leaves the others`() {
        state.show(player, row("mining"), ActionBarPriority.ACTIVITY, 100, 0)
        state.show(player, row("+5"), ActionBarPriority.FEEDBACK, 100, 0)

        state.clear(player, ActionBarPriority.ACTIVITY)

        assertEquals("+5", state.current(player, 1)?.template)
    }

    @Test
    fun `players are tracked separately`() {
        val other = UUID.randomUUID()
        state.show(player, row("mine"), ActionBarPriority.FEEDBACK, 100, 0)

        assertNull(state.current(other, 1))
        assertEquals("mine", state.current(player, 1)?.template)
    }

    @Test
    fun `an expired player stops being tracked at all`() {
        state.show(player, row("gone"), ActionBarPriority.FEEDBACK, 10, 0)
        state.current(player, 20)

        assertEquals(emptySet<UUID>(), state.trackedPlayers, "expired entries must not be held for the uptime")
    }

    @Test
    fun `clear drops a player entirely`() {
        state.show(player, row("x"), ActionBarPriority.ALERT, 100, 0)

        state.clear(player)

        assertNull(state.current(player, 1))
        assertEquals(emptySet<UUID>(), state.trackedPlayers)
    }

    @Test
    fun `a value carried on the row survives arbitration`() {
        // The values must reach the renderer as placeholders, not be folded into the template.
        state.show(player, HudRow("<danger><name> is here", listOf("name" to "<red>Impostor")), ActionBarPriority.ALERT, 100, 0)

        val current = state.current(player, 1)!!
        assertEquals("<danger><name> is here", current.template)
        assertEquals(listOf("name" to "<red>Impostor"), current.values)
    }
}
