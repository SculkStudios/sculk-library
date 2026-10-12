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
    public fun boolean(): ArgumentType<Boolean> = BuiltinArgument.BooleanArgument()

    public fun integer(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): ArgumentType<Int> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.IntegerArgument(min, max)
    }

    public fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): ArgumentType<Long> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.LongArgument(min, max)
    }

    public fun double(min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): ArgumentType<Double> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.DoubleArgument(min, max)
    }

    public fun word(maxLength: Int = Int.MAX_VALUE, pattern: Regex? = null): ArgumentType<String> =
        BuiltinArgument.StringArgument(ArgumentShape.WORD, maxLength, pattern)

    public fun <T : Any> choice(choices: Map<String, T>): ArgumentType<T> = BuiltinArgument.ChoiceArgument(choices)

    public fun uuid(): ArgumentType<java.util.UUID> = BuiltinArgument.UuidArgument()

    public fun key(): ArgumentType<net.kyori.adventure.key.Key> = BuiltinArgument.KeyArgument()

    public fun duration(min: Duration = Duration.ZERO, max: Duration = Duration.INFINITE): ArgumentType<Duration> {
        require(min <= max) { "min $min is above max $max." }
        return BuiltinArgument.DurationArgument(min, max)
    }
}
