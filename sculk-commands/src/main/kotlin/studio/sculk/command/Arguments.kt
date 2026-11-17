package studio.sculk.command

import studio.sculk.annotation.SculkStable
import kotlin.time.Duration

/**
 * The built-in argument types as values, for value flags and custom argument wrappers:
 * `flag("amount", Arguments.integer(1, 64))`. Arguments themselves are declared with the matching
 * [CommandBuilder] functions.
 */
@SculkStable
public object Arguments {
    /** Exactly `true` or `false`, in lower case. */
    public fun boolean(): ArgumentType<Boolean> = BuiltinArgument.BooleanArgument()

    /** A whole number from [min] to [max], written as digits with an optional leading `-`. Throws when [min] is above [max]. */
    public fun integer(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): ArgumentType<Int> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.IntegerArgument(min, max)
    }

    /** A whole number from [min] to [max], written as [integer] reads one. Throws when [min] is above [max]. */
    public fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): ArgumentType<Long> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.LongArgument(min, max)
    }

    /**
     * A finite number from [min] to [max], written as plain decimals such as `12`, `-0.5` or `.5`;
     * exponents, `NaN`, `Infinity`, hex and suffixes are refused. Throws when [min] is above [max].
     */
    public fun double(min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): ArgumentType<Double> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.DoubleArgument(min, max)
    }

    /**
     * One word of letters, digits and `_ - . +`, at most [maxLength] characters and matching
     * [pattern] in full when given. A value that needs other characters cannot be a word.
     */
    public fun word(maxLength: Int = Int.MAX_VALUE, pattern: Regex? = null): ArgumentType<String> =
        BuiltinArgument.StringArgument(ArgumentShape.WORD, maxLength, pattern)

    /**
     * One of the keys of [choices], ignoring case, read as its value. Each key is a word of letters,
     * digits and `_ - . +`; keys that differ only in case throw.
     */
    public fun <T : Any> choice(choices: Map<String, T>): ArgumentType<T> = BuiltinArgument.ChoiceArgument(choices)

    /** A UUID in its usual hyphenated form. */
    public fun uuid(): ArgumentType<java.util.UUID> = BuiltinArgument.UuidArgument()

    /** A namespaced key; see [BuiltinArgument.KeyArgument] for how each platform reads it. */
    public fun key(): ArgumentType<net.kyori.adventure.key.Key> = BuiltinArgument.KeyArgument()

    /**
     * A finite duration such as `90s` or `1h30m`, from [min] to [max]; the default [max] leaves it
     * unbounded. Throws when [min] is above [max].
     */
    public fun duration(min: Duration = Duration.ZERO, max: Duration = Duration.INFINITE): ArgumentType<Duration> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.DurationArgument(min, max)
    }
}
