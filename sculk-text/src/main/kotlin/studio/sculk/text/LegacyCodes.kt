package studio.sculk.text

import studio.sculk.annotation.SculkStable

/**
 * Rewrites legacy `&`/`§` colour codes into the MiniMessage tags that mean the same thing.
 *
 * MiniMessage does not understand `&c` or `&l`. Every config written before MiniMessage existed
 * does, every tutorial a server owner has ever read uses them, and a plugin that silently prints
 * `&cSomething` verbatim looks broken rather than strict. So the template is rewritten before it is
 * parsed, and both spellings work in the same string.
 *
 * ### What it deliberately does not do
 *
 * - **Only templates, never values.** [SculkMessages] injects values through `Placeholder.unparsed`,
 *   which is what stops a player called `<red>` becoming markup. Conversion happens on the template
 *   before deserialisation, so a `&c` arriving inside a *value* stays literal text. That boundary is
 *   the whole point of the class and this must not blur it.
 * - **Nothing inside a `<...>` tag.** `<hover:show_text:'Tom &b Jerry'>` would otherwise gain a
 *   `<aqua>` in the middle of a tag argument and stop parsing.
 * - **A lone `&` is left alone.** Only `&` immediately followed by a code character is a code, so
 *   "Tom & Jerry" and "&&" pass through untouched. The cost, stated plainly: a literal `&c` cannot
 *   be written. Nothing in the legacy format could express that either.
 *
 * ### Where it is not faithful
 *
 * In the legacy format a colour code *resets* formatting, so `&lBold &cRed` prints "Red" unbolded.
 * MiniMessage tags nest, so `<bold>Bold <red>Red` keeps the bold. Reproducing legacy's reset
 * semantics would mean rewriting the whole string into a flat sequence, which would then break every
 * genuine MiniMessage template that relies on nesting. Mixed strings are the rare case and nesting
 * is the more useful behaviour, so the tags are substituted one for one and this difference is
 * documented rather than papered over.
 */
@SculkStable
public object LegacyCodes {
    private const val SECTION = '§'

    private val TAGS: Map<Char, String> = mapOf(
        '0' to "black",
        '1' to "dark_blue",
        '2' to "dark_green",
        '3' to "dark_aqua",
        '4' to "dark_red",
        '5' to "dark_purple",
        '6' to "gold",
        '7' to "gray",
        '8' to "dark_gray",
        '9' to "blue",
        'a' to "green",
        'b' to "aqua",
        'c' to "red",
        'd' to "light_purple",
        'e' to "yellow",
        'f' to "white",
        'k' to "obfuscated",
        'l' to "bold",
        'm' to "strikethrough",
        'n' to "underlined",
        'o' to "italic",
        'r' to "reset",
    )

    private fun isHex(char: Char): Boolean = char.isDigit() || char.lowercaseChar() in 'a'..'f'

    /** True when [template] contains anything this would rewrite. Cheap enough to call per render. */
    @SculkStable
    public fun present(template: String): Boolean {
        for (index in 0 until template.length - 1) {
            val marker = template[index]
            if (marker != '&' && marker != SECTION) continue
            val code = template[index + 1]
            if (code.lowercaseChar() in TAGS || code == '#' || code.lowercaseChar() == 'x') return true
        }
        return false
    }

    /** Returns [template] with every legacy code replaced by its MiniMessage equivalent. */
    @SculkStable
    public fun toMiniMessage(template: String): String {
        if (!present(template)) return template

        val out = StringBuilder(template.length + 16)
        var index = 0
        var insideTag = false

        while (index < template.length) {
            val char = template[index]

            // Tag arguments are quoted text that MiniMessage parses itself; a substitution in there
            // produces a tag that no longer closes.
            if (char == '<') insideTag = true
            if (char == '>') insideTag = false

            if (insideTag || (char != '&' && char != SECTION)) {
                out.append(char)
                index++
                continue
            }

            val next = template.getOrNull(index + 1)
            if (next == null) {
                out.append(char)
                index++
                continue
            }

            // `&#RRGGBB`, the spelling BungeeCord popularised and most configs use.
            if (next == '#' && index + 8 <= template.length &&
                (index + 2 until index + 8).all { isHex(template[it]) }
            ) {
                out.append('<').append(template, index + 1, index + 8).append('>')
                index += 8
                continue
            }

            // `&x&R&R&G&G&B&B`, the spelling Spigot chose for the same thing.
            if (next.lowercaseChar() == 'x' && index + 14 <= template.length &&
                (0 until 6).all { template[index + 2 + it * 2] == char && isHex(template[index + 3 + it * 2]) }
            ) {
                out.append("<#")
                for (pair in 0 until 6) out.append(template[index + 3 + pair * 2])
                out.append('>')
                index += 14
                continue
            }

            val tag = TAGS[next.lowercaseChar()]
            if (tag == null) {
                out.append(char)
                index++
                continue
            }
            out.append('<').append(tag).append('>')
            index += 2
        }

        return out.toString()
    }
}
