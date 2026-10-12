package studio.sculk.command

import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How one argument is read. Platform modules map the built-in types onto their native argument
 * types, so the client validates and suggests; any other type travels as a string of its [shape]
 * and is parsed by [parse] on the server.
 */
@SculkStable
public interface ArgumentType<T : Any> {
    /** How much input the argument takes when a platform passes it as a string. */
    public val shape: ArgumentShape

    /** What a valid value looks like, for errors and help: `an integer from 1 to 64`. */
    public val expected: String

    /** Converts the text of one argument; a failure names what was expected and what was found. */
    public fun parse(text: String, source: CommandSource): SculkResult<T>

    /** Completions for [partial], the text typed so far. */
    public fun suggest(partial: String, source: CommandSource): List<String> = emptyList()
}

/** How much input an argument consumes. */
@SculkStable
public enum class ArgumentShape {
    /** Up to the next space. */
    WORD,

    /** A word, or a quoted string with spaces. */
    QUOTABLE,

    /** The rest of the input; only valid as the last argument. */
    GREEDY,
}

/** The argument types every platform maps natively. Sealed so a platform compiler handles each one. */
@SculkStable
public sealed class BuiltinArgument<T : Any> : ArgumentType<T> {
    protected fun invalid(found: String): SculkResult<T> = SculkFailure.InvalidInput("argument", expected, "'$found'")

    @SculkStable
    public class BooleanArgument internal constructor() : BuiltinArgument<Boolean>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "true or false"

        override fun parse(text: String, source: CommandSource): SculkResult<Boolean> = when (text.lowercase()) {
            "true" -> SculkResult.success(true)
            "false" -> SculkResult.success(false)
            else -> invalid(text)
        }

        override fun suggest(partial: String, source: CommandSource): List<String> = listOf("true", "false").filter {
            it.startsWith(partial)
        }
    }

    @SculkStable
    public class IntegerArgument internal constructor(public val min: Int, public val max: Int) : BuiltinArgument<Int>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a whole number${rangeText(min, max, Int.MIN_VALUE, Int.MAX_VALUE)}"

        override fun parse(text: String, source: CommandSource): SculkResult<Int> =
            text.toIntOrNull()?.takeIf { it in min..max }?.let { SculkResult.success(it) } ?: invalid(text)
    }

    @SculkStable
    public class LongArgument internal constructor(public val min: Long, public val max: Long) : BuiltinArgument<Long>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a whole number${rangeText(min, max, Long.MIN_VALUE, Long.MAX_VALUE)}"

        override fun parse(text: String, source: CommandSource): SculkResult<Long> =
            text.toLongOrNull()?.takeIf { it in min..max }?.let { SculkResult.success(it) } ?: invalid(text)
    }

    @SculkStable
    public class DoubleArgument internal constructor(public val min: Double, public val max: Double) : BuiltinArgument<Double>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a number${rangeText(min, max, -Double.MAX_VALUE, Double.MAX_VALUE)}"

        override fun parse(text: String, source: CommandSource): SculkResult<Double> =
            text.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }?.let { SculkResult.success(it) } ?: invalid(text)
    }

    /** Text of the given [shape], optionally limited in length and to a pattern. */
    @SculkStable
    public class StringArgument internal constructor(
        override val shape: ArgumentShape,
        public val maxLength: Int,
        public val pattern: Regex?,
    ) : BuiltinArgument<String>() {
        override val expected: String
            get() = when (shape) {
                ArgumentShape.WORD -> "a word"
                ArgumentShape.QUOTABLE -> "a word or quoted text"
                ArgumentShape.GREEDY -> "text"
            } + (if (maxLength < Int.MAX_VALUE) " of at most $maxLength characters" else "") +
                (pattern?.let { " matching ${it.pattern}" }.orEmpty())

        override fun parse(text: String, source: CommandSource): SculkResult<String> = when {
            text.isEmpty() -> invalid(text)
            text.length > maxLength -> invalid(text)
            pattern != null && !pattern.matches(text) -> invalid(text)
            else -> SculkResult.success(text)
        }
    }

    /** One of a fixed set of words, each standing for a value. Matching ignores case. */
    @SculkStable
    public class ChoiceArgument<T : Any> internal constructor(choices: Map<String, T>) : BuiltinArgument<T>() {
        public val choices: Map<String, T> = choices.mapKeys { it.key.lowercase() }

        init {
            require(this.choices.isNotEmpty()) { "A choice argument needs at least one choice." }
            require(this.choices.keys.none { ' ' in it }) { "Choices are single words." }
        }

        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "one of ${choices.keys.joinToString()}"

        override fun parse(text: String, source: CommandSource): SculkResult<T> =
            choices[text.lowercase()]?.let { SculkResult.success(it) } ?: invalid(text)

        override fun suggest(partial: String, source: CommandSource): List<String> = choices.keys.filter {
            it.startsWith(partial.lowercase())
        }
    }

    @SculkStable
    public class UuidArgument internal constructor() : BuiltinArgument<UUID>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a UUID"

        override fun parse(text: String, source: CommandSource): SculkResult<UUID> = try {
            SculkResult.success(UUID.fromString(text))
        } catch (_: IllegalArgumentException) {
            invalid(text)
        }
    }

    /** A namespaced key such as `minecraft:stone`; a bare value takes the `minecraft` namespace, as the game does. */
    @SculkStable
    public class KeyArgument internal constructor() : BuiltinArgument<Key>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a key such as minecraft:stone"

        override fun parse(text: String, source: CommandSource): SculkResult<Key> = try {
            SculkResult.success(Key.key(text))
        } catch (_: InvalidKeyException) {
            invalid(text)
        }
    }

    /** A duration written as units such as `90s`, `5m`, `1h30m` or `2d`, between [min] and [max]. */
    @SculkStable
    public class DurationArgument internal constructor(public val min: Duration, public val max: Duration) : BuiltinArgument<Duration>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String
            get() = "a duration such as 90s, 5m or 1h30m" + if (max < Duration.INFINITE) " up to $max" else ""

        override fun parse(text: String, source: CommandSource): SculkResult<Duration> {
            val parts = DURATION_PART.findAll(text.lowercase()).toList()
            if (parts.isEmpty() || parts.sumOf { it.value.length } != text.length) return invalid(text)
            var total = Duration.ZERO
            for (part in parts) {
                val amount = part.groupValues[1].toLongOrNull() ?: return invalid(text)
                total += when (part.groupValues[2]) {
                    "s" -> amount.seconds
                    "m" -> amount.minutes
                    "h" -> amount.hours
                    else -> amount.days
                }
            }
            return if (total in min..max) SculkResult.success(total) else invalid(text)
        }

        private companion object {
            val DURATION_PART = Regex("""(\d+)([smhd])""")
        }
    }
}

private fun <T : Comparable<T>> rangeText(min: T, max: T, floor: T, ceiling: T): String = when {
    min == floor && max == ceiling -> ""
    max == ceiling -> " of at least $min"
    min == floor -> " of at most $max"
    else -> " from $min to $max"
}
