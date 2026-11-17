package studio.sculk.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.execution.SculkScope
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class CronTest {
    private val executor = FakeExecutor()
    private val failures = mutableListOf<Throwable>()
    private val scope = SculkScope.root("plugin", executor) { failures += it }
    private val clock = FakeClock(Instant.parse("2026-01-01T00:00:30Z"))

    private fun every(expression: String) = (CronExpression.parse(expression) as SculkResult.Success).value

    // One tick of game time is one tick of the fake clock, as on a server running at 20 TPS.
    private fun run(ticks: Int) = repeat(ticks) {
        clock.advanceTicks(1)
        executor.tick()
    }

    @Test
    fun `a cron schedule runs at its times when the fake clock and executor advance together`() {
        val runs = mutableListOf<Instant>()
        scope.cron(every("* * * * *"), ZoneOffset.UTC, clock = clock) { runs += clock.now() }
        run(20 * 140)
        assertEquals(listOf("2026-01-01T00:01:00Z", "2026-01-01T00:02:00Z"), runs.map { it.toString() })
    }

    @Test
    fun `a lagging server runs the schedule within a second's worth of ticks of its time`() {
        val runs = mutableListOf<Instant>()
        scope.cron(every("* * * * *"), ZoneOffset.UTC, clock = clock) { runs += clock.now() }
        // Ticks that each take half a second of wall time: the server runs at a tenth of its speed.
        repeat(80) {
            clock.advance(Duration.ofMillis(500))
            executor.tick()
        }
        assertEquals(1, runs.size)
        val late = Duration.between(Instant.parse("2026-01-01T00:01:00Z"), runs.single())
        assertTrue(!late.isNegative && late <= Duration.ofSeconds(10), "ran $late late")
    }

    @Test
    fun `a cron run that throws is reported and the schedule continues`() {
        var runs = 0
        scope.cron(every("* * * * *"), ZoneOffset.UTC, clock = clock) {
            runs++
            if (runs == 1) error("one bad run")
        }
        run(20 * 140)
        assertEquals(2, runs)
        assertEquals(listOf("one bad run"), failures.map { it.message })
    }
}
