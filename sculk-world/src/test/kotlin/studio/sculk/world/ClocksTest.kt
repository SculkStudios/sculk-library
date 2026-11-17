package studio.sculk.world

import net.kyori.adventure.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.getOrThrow

@OptIn(SculkExperimental::class)
class ClocksTest {
    private val day = Timeline(TimelineKey.DAY, ClockKey.OVERWORLD, 24_000)
    private val moon = Timeline(TimelineKey.MOON, ClockKey.OVERWORLD, 192_000)
    private val earlyGame = Timeline(TimelineKey.EARLY_GAME, ClockKey.OVERWORLD, null)

    @Test
    fun `a position is the time into the current period and the periods completed`() {
        val noonOnDayThree = day.at(2 * 24_000L + 6_000)
        assertEquals(6_000L, noonOnDayThree.tick)
        assertEquals(2L, noonOnDayThree.cycle)
        assertEquals(5L, moon.at(5 * 24_000L + 100).tick / 24_000, "moon phase")
    }

    @Test
    fun `a timeline without a period has one endless cycle`() {
        val position = earlyGame.at(130_000)
        assertEquals(130_000L, position.tick)
        assertEquals(0L, position.cycle)
    }

    @Test
    fun `a timeline refuses a period that is not positive`() {
        assertThrows<IllegalArgumentException> { Timeline(TimelineKey.DAY, ClockKey.OVERWORLD, 0) }
    }

    @Test
    fun `clock and timeline keys parse with the minecraft namespace by default and refuse malformed text`() {
        assertEquals(ClockKey.OVERWORLD, ClockKey.parse("overworld").getOrThrow())
        assertEquals(ClockKey(Key.key("sculk:arena")), ClockKey.parse("sculk:arena").getOrThrow())
        assertEquals(TimelineKey.VILLAGER_SCHEDULE, TimelineKey.parse("minecraft:villager_schedule").getOrThrow())
        assertTrue(ClockKey.parse("Over World") is SculkFailure.InvalidInput)
        assertTrue(TimelineKey.parse("day!") is SculkFailure.InvalidInput)
    }

    @Test
    fun `clock states and timelines compare by value`() {
        assertEquals(ClockState(ClockKey.THE_END, 10, false, 1f), ClockState(ClockKey.THE_END, 10, false, 1f))
        assertEquals(Timeline(TimelineKey.DAY, ClockKey.OVERWORLD, 24_000), day)
        assertEquals(day.at(7), day.at(7))
    }
}
