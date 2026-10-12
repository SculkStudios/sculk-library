package studio.sculk.text

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.sculk.annotation.SculkStable

/**
 * Named, semantic styles a template uses instead of colours: `<primary>`, `<danger>`, `<muted>`.
 * Changing a theme restyles every message without touching a template.
 *
 * Styles are expanded into MiniMessage tags in the template before it is parsed. Only templates
 * are expanded, never values, so a value can never pull in a style or any other markup.
 */
@SculkStable
public class Theme private constructor(private val styles: Map<String, ThemeStyle>) {
    public val names: Set<String> get() = styles.keys

    public operator fun get(name: String): ThemeStyle? = styles[name]

    /** A copy with [name] set to [style]. */
    public fun with(name: String, style: ThemeStyle): Theme = Theme(styles + (validName(name) to style))

    /** [template] with every `<name>` and `</name>` of a style replaced by the tags that render it. */
    internal fun expand(template: String): String {
        if ('<' !in template) return template
        return TAG.replace(template) { match ->
            val (slash, name) = match.destructured
            val style = styles[name] ?: return@replace match.value
            if (slash.isEmpty()) style.open else style.close
        }
    }

    @SculkStable
    public companion object {
        private val TAG = Regex("<(/?)([a-z0-9_-]+)>")
        private val NAME = Regex("[a-z0-9_-]+")

        public val EMPTY: Theme = Theme(emptyMap())

        /** A neutral starting point; every plugin is expected to choose its own colours. */
        public val DEFAULT: Theme = of(
            "primary" to ThemeStyle.Solid("#8B7CF6"),
            "secondary" to ThemeStyle.Solid("#5EC4D6"),
            "value" to ThemeStyle.Solid("#F5F5F5"),
            "muted" to ThemeStyle.Solid("#9AA0A6"),
            "success" to ThemeStyle.Solid("#5BD08A"),
            "warning" to ThemeStyle.Solid("#F2C14E"),
            "danger" to ThemeStyle.Solid("#F2545B"),
        )

        public fun of(vararg styles: Pair<String, ThemeStyle>): Theme = Theme(
            styles.associate { (name, style) ->
                validName(name) to style
            },
        )

        private fun validName(name: String): String {
            require(NAME.matches(name)) { "Theme style '$name' must be lower-case letters, digits, '_' or '-'." }
            require(!TagResolver.standard().has(name)) { "Theme style '$name' would hide MiniMessage's own <$name> tag." }
            return name
        }
    }
}

/** How one theme style renders. */
@SculkStable
public sealed interface ThemeStyle {
    /** The MiniMessage tags that open the style. */
    public val open: String

    /** The MiniMessage tags that close it, in reverse order. */
    public val close: String

    /** One colour, optionally with decorations such as `bold` or `italic`. Keeps a copy of [decorations]. */
    @SculkStable
    public class Solid(public val hex: String, decorations: List<String> = emptyList()) : ThemeStyle {
        public val decorations: List<String> = decorations.toList()

        init {
            require(HEX.matches(hex)) { "'$hex' is not a #rrggbb colour." }
            this.decorations.forEach { require(it in DECORATIONS) { "'$it' is not a text decoration; use one of $DECORATIONS." } }
        }

        override val open: String = "<$hex>" + this.decorations.joinToString("") { "<$it>" }

        override val close: String = this.decorations.asReversed().joinToString("") { "</$it>" } + "</$hex>"

        override fun equals(other: Any?): Boolean = other is Solid && other.hex == hex && other.decorations == decorations

        override fun hashCode(): Int = 31 * hex.hashCode() + decorations.hashCode()

        override fun toString(): String = "Solid(hex=$hex, decorations=$decorations)"
    }

    /** A gradient across two or more colours. Keeps a copy of [colours]. */
    @SculkStable
    public class Gradient(colours: List<String>) : ThemeStyle {
        public val colours: List<String> = colours.toList()

        init {
            require(this.colours.size >= 2) { "A gradient needs at least two colours, but has ${this.colours.size}." }
            this.colours.forEach { require(HEX.matches(it)) { "'$it' is not a #rrggbb colour." } }
        }

        override val open: String = "<gradient:${this.colours.joinToString(":")}>"

        override val close: String = "</gradient>"

        override fun equals(other: Any?): Boolean = other is Gradient && other.colours == colours

        override fun hashCode(): Int = colours.hashCode()

        override fun toString(): String = "Gradient(colours=$colours)"
    }

    private companion object {
        val HEX = Regex("#[0-9a-fA-F]{6}")
        val DECORATIONS = setOf("bold", "italic", "underlined", "strikethrough", "obfuscated")
    }
}
