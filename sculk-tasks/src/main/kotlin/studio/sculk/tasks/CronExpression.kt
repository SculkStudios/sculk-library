package studio.sculk.tasks

import studio.sculk.core.annotation.SculkStable
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A standard 5-field cron expression: `minute hour day-of-month month day-of-week`.
 *
 * Each field supports `*`, lists (`1,2,3`), ranges (`1-5`), and step syntax such as every-15-minutes
 * (`0,15,30,45` or the slash form). Day-of-week is `0-6` with `0`/`7` = Sunday. When both
 * day-of-month and day-of-week are restricted, a time matches if **either** matches (standard cron).
 *
 * ```
 * val daily = CronExpression.parse("0 3 * * *")     // 03:00 every day
 * val next = daily.nextAfter(ZonedDateTime.now())
 * ```
 */
@SculkStable
public class CronExpression private constructor(
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val domRestricted: Boolean,
    private val dowRestricted: Boolean,
) {
    /** Returns the next instant strictly after [from] that matches this expression, or null if none within 4 years. */
    public fun nextAfter(from: ZonedDateTime): ZonedDateTime? {
        var candidate = from.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val limit = from.plusYears(4)
        while (candidate.isBefore(limit)) {
            if (matches(candidate)) return candidate
            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    private fun matches(time: ZonedDateTime): Boolean {
        if (time.minute !in minutes) return false
        if (time.hour !in hours) return false
        if (time.monthValue !in months) return false

        val domMatch = time.dayOfMonth in daysOfMonth
        // java.time DayOfWeek: MONDAY=1..SUNDAY=7; cron uses SUNDAY=0.
        val dow = time.dayOfWeek.value % 7
        val dowMatch = dow in daysOfWeek

        return when {
            domRestricted && dowRestricted -> domMatch || dowMatch
            domRestricted -> domMatch
            dowRestricted -> dowMatch
            else -> true
        }
    }

    public companion object {
        /** Parses a 5-field cron [expression], throwing [IllegalArgumentException] if malformed. */
        @SculkStable
        public fun parse(expression: String): CronExpression {
            val fields = expression.trim().split(Regex("\\s+"))
            require(fields.size == 5) { "Cron expression must have 5 fields, got ${fields.size}: '$expression'." }
            val dow = parseField(normalizeDow(fields[4]), 0, 6)
            return CronExpression(
                minutes = parseField(fields[0], 0, 59),
                hours = parseField(fields[1], 0, 23),
                daysOfMonth = parseField(fields[2], 1, 31),
                months = parseField(fields[3], 1, 12),
                daysOfWeek = dow,
                domRestricted = fields[2] != "*",
                dowRestricted = fields[4] != "*",
            )
        }

        // cron allows 7 for Sunday; map it to 0.
        private fun normalizeDow(field: String): String = field.replace("7", "0")

        private fun parseField(
            field: String,
            min: Int,
            max: Int,
        ): Set<Int> {
            val result = sortedSetOf<Int>()
            for (part in field.split(",")) {
                val (range, step) = part.split("/").let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1) }
                require(step > 0) { "Cron step must be positive in '$field'." }
                val (lo, hi) =
                    when {
                        range == "*" -> min to max
                        "-" in range -> {
                            val bounds = range.split("-")
                            (bounds[0].toInt()) to (bounds[1].toInt())
                        }
                        else -> {
                            val value = range.toInt()
                            value to value
                        }
                    }
                require(lo in min..max && hi in min..max && lo <= hi) { "Cron field value out of range [$min,$max]: '$part'." }
                var v = lo
                while (v <= hi) {
                    result += v
                    v += step
                }
            }
            return result
        }
    }
}
