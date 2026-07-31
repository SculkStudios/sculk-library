package studio.sculk.hud

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import studio.sculk.annotation.SculkInternal
import studio.sculk.scheduler.FakeScheduler
import studio.sculk.text.SculkMessages
import java.util.UUID

@OptIn(SculkInternal::class)
class HudServiceTest {
    private val scheduler = FakeScheduler()
    private val players = mutableListOf<Player>()

    private fun service() = HudService(
        scheduler = scheduler,
        messages = SculkMessages(),
        refreshTicks = 5,
        onlinePlayers = { players },
    )

    private fun player(): Player {
        val player: Player = mock()
        whenever(player.uniqueId).thenReturn(UUID.randomUUID())
        return player.also { players += it }
    }

    @Test
    fun `the hud runs one task regardless of how many players are online`() {
        repeat(50) { player() }
        val hud = service()

        hud.start()

        assertEquals(1, hud.taskCount)
        assertEquals(1, scheduler.pending.size, "one driver task, not one per player per element")
    }

    @Test
    fun `starting twice does not add a second task`() {
        val hud = service()

        hud.start()
        hud.start()

        assertEquals(1, scheduler.pending.size)
    }

    @Test
    fun `an action bar message is drawn while live and stops once it expires`() {
        val viewer = player()
        val hud = service()
        hud.start()

        // Expires at tick 10; the driver refreshes every 5.
        hud.actionBar(viewer, "hello", ActionBarPriority.FEEDBACK, durationTicks = 10)

        scheduler.advance(5)
        verify(viewer, times(1)).sendActionBar(any<Component>())

        scheduler.advance(50)
        verify(viewer, times(1)).sendActionBar(any<Component>())
    }

    @Test
    fun `a longer message is redrawn on every frame it is live for`() {
        val viewer = player()
        val hud = service()
        hud.start()

        hud.actionBar(viewer, "hello", ActionBarPriority.ACTIVITY, durationTicks = 100)

        scheduler.advance(15)

        verify(viewer, times(3)).sendActionBar(any<Component>())
    }

    @Test
    fun `closing stops the driver`() {
        val hud = service()
        hud.start()

        hud.close()

        assertEquals(0, hud.taskCount)
        assertEquals(0, scheduler.pending.size)
    }

    @Test
    fun `forgetting a player drops their state`() {
        val viewer = player()
        val hud = service()
        hud.start()
        hud.actionBar(viewer, "hi", ActionBarPriority.ALERT, durationTicks = 1000)

        hud.forget(viewer)
        scheduler.advance(10)

        verify(viewer, never()).sendActionBar(any<Component>())
    }

    @Test
    fun `one player's message does not affect another's`() {
        val loud = player()
        val quiet = player()
        val hud = service()
        hud.start()

        hud.actionBar(loud, "mine", ActionBarPriority.ALERT, durationTicks = 100)
        scheduler.advance(5)

        verify(loud, times(1)).sendActionBar(any<Component>())
        verify(quiet, never()).sendActionBar(any<Component>())
    }

    @Test
    fun `an action bar value reaches the renderer as a placeholder rather than as markup`() {
        val viewer = player()
        val hud = service()
        hud.start()

        // If the value were substituted into the template before rendering, this would parse as a
        // colour tag rather than showing literally.
        hud.actionBar(viewer, "<name> joined", ActionBarPriority.ALERT, 100, "name" to "<red>Impostor")
        scheduler.advance(5)

        verify(viewer).sendActionBar(
            org.mockito.kotlin.argThat<Component> { component ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText()
                    .serialize(component) == "<red>Impostor joined"
            },
        )
    }
}
