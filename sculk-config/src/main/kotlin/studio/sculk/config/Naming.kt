package studio.sculk.config

/**
 * File keys are kebab-case: `maxPlayers` is written `max-players`. Names that are already lower
 * case, including ones set with `@SerialName`, are kept.
 */
internal fun kebab(name: String): String = buildString(name.length + 4) {
    name.forEachIndexed { index, char ->
        if (char.isUpperCase()) {
            val previousLower = index > 0 && !name[index - 1].isUpperCase() && name[index - 1] != '-'
            val nextLower = index + 1 < name.length && name[index + 1].isLowerCase()
            if (index > 0 && (previousLower || (nextLower && name[index - 1].isUpperCase()))) append('-')
            append(char.lowercaseChar())
        } else {
            append(char)
        }
    }
}

/**
 * How an enum constant is written: `HARD_CORE` and `hardCore` both become `hard-core`. Reading
 * ignores case and separators, so files written before this rule still load.
 */
internal fun enumName(name: String): String =
    if (name.all { it.isUpperCase() || it.isDigit() || it == '_' }) name.lowercase().replace('_', '-') else kebab(name).lowercase()

/** The name a sealed subtype is selected by in `type:`: its simple name, kebab-cased. */
internal fun typeName(serialName: String): String = kebab(serialName.substringAfterLast('.').substringAfterLast('$'))

internal fun child(path: String, key: String): String = if (path.isEmpty()) key else "$path.$key"
