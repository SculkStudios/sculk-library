package studio.sculk.config

/**
 * `${NAME}` and `${NAME:-default}` read the environment; `$${NAME}` is the literal text `${NAME}`.
 * [escape] is the inverse of [resolve] for text that contains no placeholders of its own.
 */
internal object Placeholders {
    private val PLACEHOLDER = Regex("""\$(\$?)\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")
    private val UNESCAPED = Regex("""\$\{[A-Za-z_][A-Za-z0-9_]*(?::-[^}]*)?}""")

    fun resolve(text: String, environment: (String) -> String?, unset: (name: String) -> String): String {
        if ('$' !in text) return text
        return PLACEHOLDER.replace(text) { match ->
            val (escape, name) = match.destructured
            when {
                escape.isNotEmpty() -> match.value.substring(1)
                else -> environment(name) ?: match.groups[3]?.value ?: unset(name)
            }
        }
    }

    /** Resolves what can be resolved and leaves an unset placeholder as written. */
    fun resolveLeniently(text: String, environment: (String) -> String?): String = resolve(text, environment) { name -> "\${$name}" }

    fun escape(text: String): String = if ('$' !in text) text else UNESCAPED.replace(text) { match -> "$" + match.value }
}
