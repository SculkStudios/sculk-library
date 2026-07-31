package studio.sculk.config

private val PLACEHOLDER = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")

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
