package studio.sculk.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import studio.sculk.isSuccess
import java.time.ZoneId
import java.time.ZonedDateTime

class CronExpressionTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

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
    fun `an expression that can never run is refused when parsed`() {
        val failure = CronExpression.parse("0 0 31 feb *") as SculkFailure.InvalidInput
        assertEquals("a date that exists", failure.expected)
        assertTrue(CronExpression.parse("0 0 29 feb *").isSuccess)
    }

    @Test
    fun `a time skipped when the clocks go forward still runs that day, after the jump`() {
        val cron = CronExpression.parse("30 2 * * *").getOrThrow()
        val next = cron.nextAfter(ZonedDateTime.of(2026, 3, 29, 1, 0, 0, 0, amsterdam))
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, amsterdam), next)
    }

    @Test
    fun `a time repeated when the clocks go back runs once`() {
        val cron = CronExpression.parse("30 2 * * *").getOrThrow()
        val first = requireNotNull(cron.nextAfter(ZonedDateTime.of(2026, 10, 25, 1, 0, 0, 0, amsterdam)))
        assertEquals(ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, amsterdam).withEarlierOffsetAtOverlap(), first)
        assertEquals(ZonedDateTime.of(2026, 10, 26, 2, 30, 0, 0, amsterdam), cron.nextAfter(first))
    }

    @Test
    fun `a stepped day of month counts as unrestricted, as in Vixie cron`() {
        val cron = CronExpression.parse("0 9 */2 * mon").getOrThrow()
        assertEquals(at("2026-10-12T09:00:00"), cron.nextAfter(at("2026-10-12T00:00:00")))
        assertEquals(at("2026-10-19T09:00:00"), cron.nextAfter(at("2026-10-12T09:00:00")))
    }

    @Test
    fun `an invalid field explains itself`() {
        val failure = CronExpression.parse("61 * * * *") as SculkFailure.InvalidInput
        assertEquals("cron expression '61 * * * *': expected a valid cron field, found value 61 is outside 0-59.", failure.message)
        val wrongCount = CronExpression.parse("* * *") as SculkFailure.InvalidInput
        assertEquals("3 field(s)", wrongCount.received)
    }
}
