package studio.sculk.config

/**
 * The YAML key a property is written as.
 *
 * Must match kaml's `KebabCase` naming strategy exactly. Comment placement and validation paths
 * are both matched against keys in the rendered document, so a mismatch here does not fail — it
 * silently drops every comment and every constraint on the affected property.
 */
internal fun yamlKey(propertyName: String): String = buildString(propertyName.length + 4) {
    for ((index, char) in propertyName.withIndex()) {
        if (char.isUpperCase()) {
            if (index > 0) append('-')
            append(char.lowercaseChar())
        } else {
            append(char)
        }
    }
}
