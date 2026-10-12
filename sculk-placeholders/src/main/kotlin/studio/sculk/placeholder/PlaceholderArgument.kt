package studio.sculk.placeholder

import studio.sculk.annotation.SculkStable

/**
 * One argument a placeholder reads from its tag, positionally: `<shop_stat:kills>` gives `kills`
 * to the first declared argument. Arguments come from the template, so they are trusted text, but
 * they are still validated: a tag with a missing, extra or invalid argument is left in the output
 * as written, which shows the mistake where it was made.
 *
 * A placeholder's resolver reads the parsed value with `this[argument]`. Arguments are compared by
 * identity: read with the instance the placeholder was declared with.
 */
@SculkStable
public class PlaceholderArgument<T : Any> private constructor(
    public val name: String,
    /** What a valid value looks like, for errors: `an integer from 0 to 4`. */
    public val expected: String,
    private val parser: (String) -> T?,
    /** The value when the tag leaves this argument out; null when it is required. */
    public val default: T?,
) {
    init {
        require(NAME.matches(name)) { "Placeholder argument '$name' must be lower-case letters, digits, '_' or '-'." }
    }

    public val isOptional: Boolean get() = default != null

    /** This argument, optional: a tag that leaves it out reads [default]. Only the last arguments can be optional. */
    public fun optional(default: T): PlaceholderArgument<T> = PlaceholderArgument(name, expected, parser, default)

    internal fun parse(text: String): T? = parser(text)

    override fun toString(): String = "<$name>"

    @SculkStable
    public companion object {
        private val NAME = Regex("[a-z0-9_-]+")

        /** Any text, or text matching [pattern] in full. */
        public fun text(name: String, pattern: Regex? = null): PlaceholderArgument<String> {
            val expected = pattern?.let { "text matching ${it.pattern}" } ?: "text"
            return PlaceholderArgument(name, expected, { text -> text.takeIf { pattern == null || pattern.matches(it) } }, null)
        }

        /** A whole number from [min] to [max]. */
        public fun integer(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): PlaceholderArgument<Int> {
            require(min <= max) { "<$name> has min $min above max $max." }
            val range = when {
                min == Int.MIN_VALUE && max == Int.MAX_VALUE -> ""
                min == Int.MIN_VALUE -> " of at most $max"
                max == Int.MAX_VALUE -> " of at least $min"
                else -> " from $min to $max"
            }
            return PlaceholderArgument(name, "a whole number$range", { text -> text.toIntOrNull()?.takeIf { it in min..max } }, null)
        }

        /** One of [choices], by key; matching ignores case. */
        public fun <T : Any> choice(name: String, choices: Map<String, T>): PlaceholderArgument<T> {
            require(choices.isNotEmpty()) { "<$name> needs at least one choice." }
            val byKey = choices.mapKeys { it.key.lowercase() }
            require(byKey.size == choices.size) { "<$name> has choices that differ only in case." }
            return PlaceholderArgument(name, "one of ${choices.keys.joinToString(", ")}", { byKey[it.lowercase()] }, null)
        }

        /** An argument read by [parse], which returns null for text that is not a valid value. */
        public fun <T : Any> of(name: String, expected: String, parse: (String) -> T?): PlaceholderArgument<T> =
            PlaceholderArgument(name, expected, parse, null)
    }
}
