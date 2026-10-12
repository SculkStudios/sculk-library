package studio.sculk.task

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.BitSet

/**
 * A five-field cron schedule: minute, hour, day of month, month, day of week.
 *
 * Fields accept `*`, numbers, `a-b` ranges, `/n` steps and comma lists; months and days of the
 * week also accept three-letter English names, and day of week 7 is Sunday like 0. When both day
 * fields are restricted, a day matches if either does, as in Vixie cron.
 */
@SculkStable
public class CronExpression private constructor(
    public val source: String,
    private val minutes: BitSet,
    private val hours: BitSet,
    private val daysOfMonth: BitSet,
    private val months: BitSet,
    private val daysOfWeek: BitSet,
    private val dayOfMonthRestricted: Boolean,
    private val dayOfWeekRestricted: Boolean,
) {
    /** The first matching minute strictly after [time], or null when none exists within five years. */
    public fun nextAfter(time: ZonedDateTime): ZonedDateTime? {
        var candidate = time.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val limit = time.plusYears(SEARCH_YEARS)
        while (candidate.isBefore(limit)) {
            candidate = when {
                !months[candidate.monthValue] -> candidate.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1)
                !dayMatches(candidate) -> candidate.truncatedTo(ChronoUnit.DAYS).plusDays(1)
                !hours[candidate.hour] -> candidate.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                !minutes[candidate.minute] -> candidate.plusMinutes(1)
                else -> return candidate
            }
        }
        return null
    }

    private fun dayMatches(time: ZonedDateTime): Boolean {
        val dom = daysOfMonth[time.dayOfMonth]
        val dow = daysOfWeek[time.dayOfWeek.value % 7]
        return when {
            dayOfMonthRestricted && dayOfWeekRestricted -> dom || dow
            dayOfMonthRestricted -> dom
            dayOfWeekRestricted -> dow
            else -> true
        }
    }

    override fun equals(other: Any?): Boolean = other is CronExpression && other.source == source

    override fun hashCode(): Int = source.hashCode()

    override fun toString(): String = source

    @SculkStable
    public companion object {
        private const val SEARCH_YEARS = 5L
        private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        private val DAY_NAMES = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

        /** Parses [expression], explaining the first invalid field on failure. */
        public fun parse(expression: String): SculkResult<CronExpression> {
            val fields = expression.trim().split(Regex("\\s+"))
            if (fields.size != 5) {
                return invalid(expression, "five fields separated by spaces", "${fields.size} field(s)")
            }
            return try {
                val dow = field(fields[4], 0, 7, DAY_NAMES, offset = 0).also { if (it[7]) it.set(0) }
                SculkResult.success(
                    CronExpression(
                        source = fields.joinToString(" "),
                        minutes = field(fields[0], 0, 59),
                        hours = field(fields[1], 0, 23),
                        daysOfMonth = field(fields[2], 1, 31),
                        months = field(fields[3], 1, 12, MONTH_NAMES, offset = 1),
                        daysOfWeek = dow,
                        dayOfMonthRestricted = fields[2] != "*",
                        dayOfWeekRestricted = fields[4] != "*",
                    ),
                )
            } catch (bad: IllegalArgumentException) {
                invalid(expression, "a valid cron field", bad.message ?: "an invalid field")
            }
        }

        private fun invalid(expression: String, expected: String, received: String) =
            SculkFailure.InvalidInput("cron expression '$expression'", expected, received)

        private fun field(text: String, min: Int, max: Int, names: List<String> = emptyList(), offset: Int = 0): BitSet {
            val bits = BitSet(max + 1)
            for (part in text.split(',')) {
                val (rangeText, stepText) = part.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
                val step = stepText?.let { parseNumber(it, "step", 1, max) } ?: 1
                val (from, to) = when {
                    rangeText == "*" -> min to max

                    '-' in rangeText -> rangeText.split('-', limit = 2).let {
                        value(it[0], min, max, names, offset) to
                            value(it[1], min, max, names, offset)
                    }

                    stepText != null -> value(rangeText, min, max, names, offset) to max

                    else -> value(rangeText, min, max, names, offset).let { it to it }
                }
                require(from <= to) { "'$part' runs backwards" }
                for (v in from..to step step) bits.set(v)
            }
            return bits
        }

        private fun value(text: String, min: Int, max: Int, names: List<String>, offset: Int): Int {
            val named = names.indexOf(text.lowercase())
            return if (named >= 0) named + offset else parseNumber(text, "value", min, max)
        }

        private fun parseNumber(text: String, what: String, min: Int, max: Int): Int {
            val number = text.toIntOrNull() ?: throw IllegalArgumentException("'$text' is not a number")
            require(number in min..max) { "$what $number is outside $min-$max" }
            return number
        }
    }
}
