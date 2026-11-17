package studio.sculk.command

import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
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
    /**
     * One unquoted word, as Brigadier reads it: only ASCII letters, digits and `_ - . +`. A value
     * with any other character, such as `#ff0000` or a URL, needs [QUOTABLE] or [GREEDY].
     */
    WORD,

    /** A word as [WORD] reads it, or a quoted string that may hold any character, spaces included. */
    QUOTABLE,

    /** The rest of the input; only valid as the last argument. */
    GREEDY,
}

/**
 * An argument type its platform reads with a native argument type, which defines its own syntax:
 * its text is not limited to the characters of [ArgumentShape.WORD], and [CommandDispatcher] hands
 * the whole token to [ArgumentType.parse].
 */
@SculkInternal
public interface NativeArgument

/** The argument types every platform maps natively. Sealed so a platform compiler handles each one. */
@SculkStable
public sealed class BuiltinArgument<T : Any> : ArgumentType<T> {
    protected fun invalid(found: String): SculkResult<T> = SculkFailure.InvalidInput("argument", expected, "'$found'")

    @SculkStable
    public class BooleanArgument internal constructor() : BuiltinArgument<Boolean>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "true or false"

        /** Exactly `true` or `false`; Brigadier's boolean is case-sensitive. */
        override fun parse(text: String, source: CommandSource): SculkResult<Boolean> = when (text) {
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

        override fun parse(text: String, source: CommandSource): SculkResult<Int> {
            val value = if (WHOLE_NUMBER.matches(text)) text.toIntOrNull() else null
            return if (value != null && value in min..max) SculkResult.success(value) else invalid(text)
        }
    }

    @SculkStable
    public class LongArgument internal constructor(public val min: Long, public val max: Long) : BuiltinArgument<Long>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a whole number${rangeText(min, max, Long.MIN_VALUE, Long.MAX_VALUE)}"

        override fun parse(text: String, source: CommandSource): SculkResult<Long> {
            val value = if (WHOLE_NUMBER.matches(text)) text.toLongOrNull() else null
            return if (value != null && value in min..max) SculkResult.success(value) else invalid(text)
        }
    }

    @SculkStable
    public class DoubleArgument internal constructor(public val min: Double, public val max: Double) : BuiltinArgument<Double>() {
        override val shape: ArgumentShape get() = ArgumentShape.WORD
        override val expected: String get() = "a number${rangeText(min, max, -Double.MAX_VALUE, Double.MAX_VALUE)}"

        override fun parse(text: String, source: CommandSource): SculkResult<Double> {
            val value = if (DECIMAL.matches(text)) text.toDoubleOrNull() else null
            return if (value != null && value.isFinite() && value in min..max) SculkResult.success(value) else invalid(text)
        }
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

    /**
     * One of a fixed set of words, each standing for a value. Matching ignores case, so two choices
     * that differ only in case are refused; each choice is a word as [ArgumentShape.WORD] reads it.
     */
    @SculkStable
    public class ChoiceArgument<T : Any> internal constructor(choices: Map<String, T>) : BuiltinArgument<T>() {
        public val choices: Map<String, T> = choices.mapKeys { it.key.lowercase() }

        init {
            require(this.choices.isNotEmpty()) { "A choice argument needs at least one choice." }
            require(this.choices.size == choices.size) {
                val clashes = choices.keys.groupBy { it.lowercase() }.values.filter { it.size > 1 }
                "Choices ${clashes.joinToString { it.joinToString(" and ") }} differ only in case."
            }
            require(this.choices.keys.all(::isUnquotedWord)) { "Choices are single words of letters, digits, '_', '-', '.' or '+'." }
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

    /**
     * A namespaced key such as `minecraft:stone`; a bare value takes the `minecraft` namespace, as the
     * game does. Paper reads keys natively. Velocity has no key type and reads a word, which cannot
     * hold `:` or `/`, so on a proxy only a bare value such as `stone` is accepted.
     */
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

    /** A finite duration written as units such as `90s`, `5m`, `1h30m` or `2d`, between [min] and [max]. */
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
            // Amounts too large for a Duration saturate to INFINITE rather than failing.
            return if (total.isFinite() && total in min..max) SculkResult.success(total) else invalid(text)
        }

        private companion object {
            val DURATION_PART = Regex("""(\d+)([smhd])""")
        }
    }
}

// Brigadier reads a number from `0-9 . -` only, so `+5`, `1e3`, `NaN`, `0x1F` and `5d` fail on a server.
private val WHOLE_NUMBER = Regex("-?[0-9]+")
private val DECIMAL = Regex("""-?([0-9]+(\.[0-9]*)?|\.[0-9]+)""")

/** True when [char] may appear in an unquoted word; Brigadier's `StringReader.isAllowedInUnquotedString`. */
internal fun isUnquotedChar(char: Char): Boolean =
    char in '0'..'9' || char in 'A'..'Z' || char in 'a'..'z' || char == '_' || char == '-' || char == '.' || char == '+'

/** True when [word] is a non-empty word a server reads unquoted. */
internal fun isUnquotedWord(word: String): Boolean = word.isNotEmpty() && word.all(::isUnquotedChar)

private fun <T : Comparable<T>> rangeText(min: T, max: T, floor: T, ceiling: T): String = when {
    min == floor && max == ceiling -> ""
    max == ceiling -> " of at least $min"
    min == floor -> " of at most $max"
    else -> " from $min to $max"
}
