package studio.sculk.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CronExpressionTest {
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)

    @Test
    fun `daily at 3am finds next occurrence`() {
        val cron = CronExpression.parse("0 3 * * *")
        val next = cron.nextAfter(at(2026, 5, 28, 10, 0))
        assertEquals(at(2026, 5, 29, 3, 0), next)
    }

    @Test
    fun `step every 15 minutes`() {
        val cron = CronExpression.parse("*/15 * * * *")
        val next = cron.nextAfter(at(2026, 5, 28, 10, 7))
        assertEquals(at(2026, 5, 28, 10, 15), next)
    }

    @Test
    fun `day of week restriction matches monday`() {
        // 2026-06-01 is a Monday.
        val cron = CronExpression.parse("30 9 * * 1")
        val next = cron.nextAfter(at(2026, 5, 28, 0, 0))
        assertEquals(at(2026, 6, 1, 9, 30), next)
    }

    @Test
    fun `malformed expression is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CronExpression.parse("0 3 * *") }
        assertThrows(IllegalArgumentException::class.java) { CronExpression.parse("99 3 * * *") }
    }
}
