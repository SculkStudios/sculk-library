package studio.sculk.text

import studio.sculk.annotation.SculkInternal

/** The one naming rule for theme styles, placeholder names and placeholder arguments. Not API. */
@SculkInternal
public object TagNames {
    /** One or more lower-case letters, digits, `_` or `-`, as a regular expression fragment. */
    internal const val PATTERN: String = "[a-z0-9_-]+"

    private val NAME = Regex(PATTERN)

    /** Returns [name] when it follows the rule; otherwise throws [IllegalArgumentException] describing it as [what]. */
    public fun requireValid(name: String, what: String): String {
        require(NAME.matches(name)) { "$what '$name' must be lower-case letters, digits, '_' or '-'." }
        return name
    }
}
