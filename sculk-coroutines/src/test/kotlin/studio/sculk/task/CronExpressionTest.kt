package studio.sculk.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import java.time.ZoneId
import java.time.ZonedDateTime

class CronExpressionTest {
    private val utc = ZoneId.of("UTC")

    private fun at(text: String): ZonedDateTime = ZonedDateTime.parse("${text}Z[UTC]")

    @ParameterizedTest(name = "{0} after {1} is {2}")
    @CsvSource(
        "'*/15 * * * *', 2026-10-12T10:07:30, 2026-10-12T10:15:00",
        "'0 4 * * *', 2026-10-12T04:00:00, 2026-10-13T04:00:00",
        "'30 18 * * mon-fri', 2026-10-10T12:00:00, 2026-10-12T18:30:00",
        "'0 0 1 jan *', 2026-10-12T00:00:00, 2027-01-01T00:00:00",
        "'0 12 29 feb *', 2026-03-01T00:00:00, 2028-02-29T12:00:00",
        "'0 9 * * 7', 2026-10-12T00:00:00, 2026-10-18T09:00:00",
        "'0 9 13 * fri', 2026-10-12T00:00:00, 2026-10-13T09:00:00",
        "'5,35 */6 * * *', 2026-10-12T06:06:00, 2026-10-12T06:35:00",
    )
    fun `the next run is the first matching minute strictly after the given time`(expression: String, after: String, expected: String) {
        val cron = CronExpression.parse(expression).getOrThrow()
        assertEquals(at(expected), cron.nextAfter(at(after)))
    }

    @Test
    fun `an impossible date never matches rather than looping forever`() {
        assertEquals(null, CronExpression.parse("0 0 31 feb *").getOrThrow().nextAfter(ZonedDateTime.now(utc)))
    }

    @Test
    fun `daylight saving changes are followed rather than skipped`() {
        val amsterdam = ZoneId.of("Europe/Amsterdam")
        val cron = CronExpression.parse("30 2 * * *").getOrThrow()
        val next = cron.nextAfter(ZonedDateTime.of(2026, 3, 29, 1, 0, 0, 0, amsterdam))
        assertTrue(next != null && next.dayOfMonth >= 29)
    }

    @Test
    fun `an invalid field explains itself`() {
        val failure = CronExpression.parse("61 * * * *") as SculkFailure.InvalidInput
        assertEquals("cron expression '61 * * * *': expected a valid cron field, found value 61 is outside 0-59.", failure.message)
        val wrongCount = CronExpression.parse("* * *") as SculkFailure.InvalidInput
        assertEquals("3 field(s)", wrongCount.received)
    }
}
