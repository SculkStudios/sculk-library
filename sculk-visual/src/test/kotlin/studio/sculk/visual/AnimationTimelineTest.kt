package studio.sculk.visual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.scheduler.FakeScheduler

class AnimationTimelineTest {
    @Test
    fun `steps fire in tick order regardless of declaration order`() {
        val order = mutableListOf<Int>()
        val timeline = timeline {
            at(0) { order += 0 }
            at(10) { order += 10 }
            at(5) { order += 5 }
        }
        val scheduler = FakeScheduler()

        timeline.start(scheduler)
        scheduler.advance(20)

        assertEquals(listOf(0, 5, 10), order)
    }

    @Test
    fun `a step does not fire before its tick`() {
        val fired = mutableListOf<Int>()
        val timeline = timeline {
            at(0) { fired += 0 }
            at(10) { fired += 10 }
        }
        val scheduler = FakeScheduler()

        timeline.start(scheduler)
        scheduler.advance(9)

        assertEquals(listOf(0), fired, "the step at tick 10 must still be waiting")
    }

    @Test
    fun `loop repeats every step the given number of times`() {
        val fired = mutableListOf<Long>()
        val timeline = timeline {
            at(0) { fired += 0 }
            at(10) { fired += 10 }
            loop(3)
        }
        val scheduler = FakeScheduler()

        timeline.start(scheduler)

        assertEquals(6, scheduler.pending.size, "3 loops of 2 steps")
    }

    @Test
    fun `an empty timeline schedules nothing`() {
        val scheduler = FakeScheduler()

        timeline {}.start(scheduler)

        assertEquals(0, scheduler.pending.size)
    }
}

class AnimationSequenceTest {
    @Test
    fun `steps fire at accumulated delay offsets`() {
        val order = mutableListOf<Int>()
        val sequence = sequence {
            step { order += 0 }
            delay(5)
            step { order += 5 }
            delay(10)
            step { order += 15 }
        }
        val scheduler = FakeScheduler()

        sequence.start(scheduler)
        scheduler.advance(20)

        assertEquals(listOf(0, 5, 15), order)
    }

    @Test
    fun `a delay actually delays rather than only ordering`() {
        val order = mutableListOf<Int>()
        val sequence = sequence {
            step { order += 0 }
            delay(10)
            step { order += 10 }
        }
        val scheduler = FakeScheduler()

        sequence.start(scheduler)
        scheduler.advance(5)

        assertEquals(listOf(0), order)

        scheduler.advance(5)
        assertEquals(listOf(0, 10), order)
    }

    @Test
    fun `an empty sequence schedules nothing`() {
        val scheduler = FakeScheduler()

        sequence {}.start(scheduler)

        assertEquals(0, scheduler.pending.size)
    }
}
