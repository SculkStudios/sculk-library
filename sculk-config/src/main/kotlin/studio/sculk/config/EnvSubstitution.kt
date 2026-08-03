package studio.sculk.config

private val PLACEHOLDER = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")

// `key: value`, or a sequence item, with the value captured. Anything else -- a bare continuation
// line inside a block scalar, a comment, a blank -- is left alone rather than guessed at.
private val VALUE_LINE = Regex("""^(\s*(?:- )?[^\s#:][^:]*:[ \t]+|\s*- )(.+)$""")

/**
 * Replaces `${VAR}` and `${VAR:-default}` with environment values before the YAML is parsed.
 *
 * So a database password can live in the host's environment while the shipped file still reads as
 * a working example. Substitution happens on the text, not on decoded values, because a config
 * shape has no way to say "this Int comes from the environment".
 *
 * An unset variable with no default is left exactly as written rather than blanked. A silently
 * empty password produces a connection error three steps away from the cause; a literal
 * `${DB_PASSWORD}` in the log names it.
 */
internal fun substituteEnvironment(text: String, environment: (String) -> String? = System::getenv): String =
    PLACEHOLDER.replace(text) { match ->
        val name = match.groupValues[1]
        val hasDefault = match.groupValues[2].isNotEmpty() || match.value.contains(":-")
        environment(name) ?: if (hasDefault) match.groupValues[2] else match.value
    }

/**
 * Quotes rendered values that contain a placeholder, so substituting them cannot change the shape
 * of the document.
 *
 * kaml emits `${DB_PASSWORD:-}` as a plain scalar, which is correct YAML — but [substituteEnvironment]
 * runs over the *text* before it is parsed, so an unset variable with an empty default leaves
 * `password:` with nothing after it. That is a null in YAML, and a non-null `String` property then
 * refuses to decode. The file the framework generated on the first boot stops parsing on the second,
 * which reads as the plugin having broken overnight rather than as a rendering bug.
 *
 * Quoting is done here rather than by widening kaml's [com.charleskorn.kaml.SingleLineStringStyle],
 * because quoting *every* string turns a config a server owner reads into one they have to escape.
 */
internal fun quotePlaceholders(yaml: String): String = yaml.lines().joinToString("\n") { line ->
    val match = VALUE_LINE.find(line)
    val value = match?.groupValues?.get(2)
    when {
        value == null || !PLACEHOLDER.containsMatchIn(value) -> line

        // Already quoted by kaml because the value was ambiguous for some other reason.
        value.startsWith('"') || value.startsWith('\'') -> line

        else -> match.groupValues[1] + "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
