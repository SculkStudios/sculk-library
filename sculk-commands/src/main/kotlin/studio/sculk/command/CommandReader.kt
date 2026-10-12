package studio.sculk.command

import studio.sculk.annotation.SculkStable

/**
 * A cursor over command input, with Brigadier's quoting rules so the interpreter and the compiled
 * trees read the same text the same way: words stop at a space, quoted strings take `"` or `'` and
 * `\` escapes, and a greedy read takes the rest of the line.
 */
@SculkStable
public class CommandReader(public val input: String, cursor: Int = 0) {
    public var cursor: Int = cursor
        private set

    public val remaining: String get() = input.substring(cursor)

    public val canRead: Boolean get() = cursor < input.length

    public fun peek(): Char = input[cursor]

    /** Moves past one space if the cursor is on one; arguments are separated by exactly one. */
    public fun skipSeparator(): Boolean {
        if (!canRead || peek() != ' ') return false
        cursor++
        return true
    }

    /** Reads up to the next space. */
    public fun readWord(): String {
        val start = cursor
        while (canRead && peek() != ' ') cursor++
        return input.substring(start, cursor)
    }

    /** Reads a quoted string, or a bare word when the next character is not a quote. Null when a quote is left open. */
    public fun readQuotable(): String? {
        if (!canRead) return ""
        val quote = peek()
        if (quote != '"' && quote != '\'') return readWord()
        val start = cursor
        cursor++
        val out = StringBuilder()
        var escaped = false
        while (canRead) {
            val c = input[cursor++]
            when {
                escaped -> {
                    out.append(c)
                    escaped = false
                }

                c == '\\' -> escaped = true

                c == quote -> return out.toString()

                else -> out.append(c)
            }
        }
        cursor = start
        return null
    }

    /** Reads everything that is left. */
    public fun readRest(): String = remaining.also { cursor = input.length }

    /** Moves the cursor back to [position], for a parser that read too far. */
    public fun reset(position: Int) {
        require(position in 0..input.length) { "Cursor $position is outside the input." }
        cursor = position
    }
}
