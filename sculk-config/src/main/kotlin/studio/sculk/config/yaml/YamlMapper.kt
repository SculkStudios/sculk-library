package studio.sculk.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import studio.sculk.annotation.SculkInternal
import studio.sculk.config.annotation.Comment
import studio.sculk.config.annotation.Max
import studio.sculk.config.annotation.Min
import studio.sculk.config.annotation.NotEmpty
import java.io.File
import java.io.StringWriter
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/**
 * Internal YAML mapper: reads/writes data class instances from/to YAML files.
 *
 * Supports: String, Int, Long, Double, Float, Boolean, List<T>, Map<String, V>,
 * Enum, UUID, and nested data classes.
 *
 * Keys are kebab-case in YAML, camelCase in Kotlin. Reading accepts BOTH forms, so a
 * hand-written camelCase file still maps instead of silently falling back to defaults.
 * Unrecognised keys and unusable values are reported through the optional `onWarning`
 * callback (with a did-you-mean suggestion where possible) — a config edit should never
 * just do nothing without telling the operator why.
 */
@SculkInternal
public object YamlMapper {
    internal val yaml: Yaml by lazy {
        val opts =
            DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                // isPrettyFlow=false keeps empty collections compact (`[]` / `{}`) instead of
                // splitting them across lines; a huge width + no split-lines stops long message
                // strings from wrapping. Together these keep generated configs clean and editable.
                isPrettyFlow = false
                indent = 2
                width = Int.MAX_VALUE
                splitLines = false
            }
        Yaml(opts)
    }

    /**
     * Reads [file] and maps it to an instance of [klass]. Missing keys use constructor defaults.
     * Ignored keys and rejected values are reported through [onWarning].
     */
    @JvmOverloads
    public fun <T : Any> load(file: File, klass: KClass<T>, onWarning: (String) -> Unit = {}): T {
        val raw = loadRaw(file)
        return fromMap(raw, klass, path = "", onWarning = onWarning)
    }

    /** Writes [instance] to [file] as YAML, creating parent directories if needed. */
    public fun <T : Any> save(file: File, instance: T) {
        file.parentFile?.mkdirs()
        val map = toMap(instance)
        val writer = StringWriter()
        yaml.dump(map, writer)
        file.writeText(writer.toString())
    }

    /** Reads raw YAML content as a mutable map. */
    public fun loadRaw(file: File): MutableMap<String, Any?> = if (file.exists() && file.length() > 0) {
        @Suppress("UNCHECKED_CAST")
        (yaml.load(file.readText()) as? Map<String, Any?>).orEmpty().toMutableMap()
    } else {
        mutableMapOf()
    }

    /** Writes raw YAML map content. */
    public fun saveRaw(file: File, values: Map<String, Any?>) {
        file.parentFile?.mkdirs()
        val writer = StringWriter()
        yaml.dump(values, writer)
        file.writeText(writer.toString())
    }

    /** Returns a [klass] instance built purely from constructor defaults. */
    public fun <T : Any> defaults(klass: KClass<T>): T = createDefault(klass)

    /** Writes defaults for [klass] to [file] only for keys not already present. */
    public fun <T : Any> writeDefaults(file: File, klass: KClass<T>) {
        val existing: Map<String, Any?> =
            if (file.exists() && file.length() > 0) {
                @Suppress("UNCHECKED_CAST")
                yaml.load(file.readText()) as? Map<String, Any?> ?: emptyMap()
            } else {
                emptyMap()
            }
        val defaults = toMap(createDefault(klass))
        val merged = defaults + existing
        file.parentFile?.mkdirs()

        val hasComments =
            klass.primaryConstructor
                ?.parameters
                ?.any { it.annotations.any { a -> a is Comment } } == true

        if (hasComments) {
            file.writeText(buildCommentedYaml(klass, merged))
        } else {
            val writer = StringWriter()
            yaml.dump(merged, writer)
            file.writeText(writer.toString())
        }
    }

    /**
     * Builds a YAML string that preserves parameter order from the primary constructor
     * and emits `# comment` lines above any parameter annotated with [@Comment][Comment].
     *
     * For each parameter:
     * - If annotated with [@Comment][Comment], every line of the comment text is written as `# line`
     * - The key-value pair is serialised using SnakeYAML so all types (nested data classes,
     *   lists, maps) are handled correctly
     * - A blank line is inserted after commented entries for readability
     */
    private fun <T : Any> buildCommentedYaml(klass: KClass<T>, mergedMap: Map<String, Any?>): String {
        val constructor =
            klass.primaryConstructor
                ?: return yaml.dump(mergedMap)

        val sb = StringBuilder()
        for (param in constructor.parameters) {
            val key = camelToKebab(param.name!!)
            val comment =
                param.annotations
                    .filterIsInstance<Comment>()
                    .firstOrNull()
                    ?.value
            val value = mergedMap[key]

            if (comment != null) {
                comment.lines().forEach { line -> sb.appendLine("# $line") }
            }

            // Serialise just this key-value pair via SnakeYAML so we handle all types correctly.
            val entryYaml =
                buildString {
                    val writer = StringWriter()
                    yaml.dump(mapOf(key to value), writer)
                    // SnakeYAML may prepend "--- \n" — strip it.
                    append(writer.toString().removePrefix("---\n").trimEnd())
                }
            sb.appendLine(entryYaml)
            if (comment != null) sb.appendLine() // blank line after commented blocks for readability
        }
        return sb.toString()
    }

    /** Validates an [instance] against its field annotations. Returns a list of violation messages. */
    public fun <T : Any> validate(instance: T): List<String> {
        val violations = mutableListOf<String>()
        val klass = instance::class
        for (param in klass.primaryConstructor?.parameters ?: emptyList()) {
            val value = klass.members.firstOrNull { it.name == param.name }?.call(instance)
            validateParam(param, value, violations)
        }
        return violations
    }

    /**
     * Returns the `configVersion` default value for [klass], or null if the class
     * does not declare a `configVersion` parameter.
     */
    internal fun <T : Any> defaultConfigVersion(klass: KClass<T>): Int? {
        val constructor = klass.primaryConstructor ?: return null
        if (constructor.parameters.none { it.name == "configVersion" }) return null
        return try {
            val instance = createDefault(klass)
            @Suppress("UNCHECKED_CAST")
            klass.members.firstOrNull { it.name == "configVersion" }?.call(instance) as? Int
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads the `config-version` value from [file]'s YAML content, or null if absent or unreadable.
     */
    internal fun fileConfigVersion(file: File): Int? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val raw = yaml.load<Any>(file.readText()) as? Map<String, Any?> ?: return null
            (raw["config-version"] as? Number)?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private fun <T : Any> fromMap(map: Map<String, Any?>, klass: KClass<T>, path: String = "", onWarning: (String) -> Unit = {}): T {
        val constructor =
            requireNotNull(klass.primaryConstructor) {
                "Config class ${klass.simpleName} must have a primary constructor."
            }
        val args = mutableMapOf<KParameter, Any?>()
        val knownKeys = mutableSetOf("config-version")
        for (param in constructor.parameters) {
            val name = param.name!!
            val kebab = camelToKebab(name)
            knownKeys += kebab
            knownKeys += name
            // Canonical files are kebab-case, but a hand-written camelCase key must still map —
            // silently falling back to the default is how config edits "do nothing".
            val raw = map[kebab] ?: map[name]
            if (raw != null) {
                val coerced = coerce(raw, param.type, keyPath(path, kebab), onWarning)
                when {
                    coerced == null -> Unit

                    // coerce already warned; the default applies.

                    isAssignable(coerced, param.type) -> args[param] = coerced

                    else -> {
                        onWarning(
                            "${keyPath(path, kebab)}: '$raw' is not a valid ${typeName(param.type)}; using the default value.",
                        )
                    }
                }
            }
            // Missing keys keep the constructor default — Kotlin handles this automatically.
        }
        for (key in map.keys - knownKeys) {
            val suggestion =
                knownKeys
                    .minus("config-version")
                    .filter { it.contains('-') || !it.any(Char::isUpperCase) } // suggest the canonical kebab form
                    .minByOrNull { levenshtein(key, it) }
                    ?.takeIf { levenshtein(key, it) <= 2 }
            onWarning(
                buildString {
                    append("'${keyPath(path, key)}' is not a recognised option and was ignored")
                    if (suggestion != null) append(" — did you mean '$suggestion'?") else append(".")
                },
            )
        }
        return constructor.callBy(args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerce(value: Any?, type: KType, path: String, onWarning: (String) -> Unit): Any? {
        if (value == null) return null

        // List<T> — entries that fail to coerce were already warned about and are skipped, so
        // one broken entry can't take the whole config (or the plugin) down with it.
        if (type.classifier == List::class) {
            val elementType = type.arguments.firstOrNull()?.type ?: return value
            val list = value as? List<*> ?: return emptyList<Any?>()
            return list.mapIndexedNotNull { index, item -> coerce(item, elementType, "$path[$index]", onWarning) }
        }

        // Map<String, V>
        if (type.classifier == Map::class) {
            val valueType = type.arguments.getOrNull(1)?.type ?: return value
            return (value as? Map<*, *>)
                ?.mapValues { (key, v) -> coerce(v, valueType, "$path.$key", onWarning) }
                ?: emptyMap<String, Any?>()
        }

        val klass = type.classifier as? KClass<*> ?: return value

        // UUID
        if (klass == UUID::class) {
            return runCatching { UUID.fromString(value.toString()) }.getOrNull()
                ?: rejected(path, "'$value' is not a valid UUID", onWarning)
        }

        // Enum — an unknown constant must not crash the load with a reflection error; report the
        // valid options and let the default apply.
        if (klass.java.isEnum) {
            val enumClass = klass.java as Class<out Enum<*>>
            return enumClass.enumConstants.firstOrNull { it.name.equals(value.toString(), ignoreCase = true) }
                ?: rejected(
                    path,
                    "'$value' is not one of ${enumClass.enumConstants.joinToString(", ") { it.name }}",
                    onWarning,
                )
        }

        return when (klass) {
            // Numbers and booleans also accept quoted strings ("5", "true") — SnakeYAML keeps
            // quoted scalars as strings, and that distinction shouldn't break anyone's config.
            Int::class -> (value as? Number)?.toInt() ?: value.toString().trim().toIntOrNull()
                ?: rejected(path, "'$value' is not a whole number", onWarning)

            Long::class -> (value as? Number)?.toLong() ?: value.toString().trim().toLongOrNull()
                ?: rejected(path, "'$value' is not a whole number", onWarning)

            Double::class -> (value as? Number)?.toDouble() ?: value.toString().trim().toDoubleOrNull()
                ?: rejected(path, "'$value' is not a number", onWarning)

            Float::class -> (value as? Number)?.toFloat() ?: value.toString().trim().toFloatOrNull()
                ?: rejected(path, "'$value' is not a number", onWarning)

            Boolean::class -> value as? Boolean ?: value.toString().trim().lowercase().toBooleanStrictOrNull()
                ?: rejected(path, "'$value' is not true/false", onWarning)

            String::class -> substituteEnv(value.toString())

            else -> when {
                value is Map<*, *> -> fromMap(value as Map<String, Any?>, klass, path, onWarning)
                klass.isData -> rejected(path, "expected a section with keys, got '$value'", onWarning)
                else -> value
            }
        }
    }

    /** Reports an unusable value and returns null so the constructor default applies instead. */
    private fun rejected(path: String, problem: String, onWarning: (String) -> Unit): Any? {
        onWarning("$path: $problem; using the default value.")
        return null
    }

    private fun isAssignable(value: Any, type: KType): Boolean {
        val klass = type.classifier as? KClass<*> ?: return true
        return klass.isInstance(value)
    }

    private fun typeName(type: KType): String = (type.classifier as? KClass<*>)?.simpleName ?: type.toString()

    private fun keyPath(path: String, key: String): String = if (path.isEmpty()) key else "$path.$key"

    /** Plain Levenshtein distance, used only for short config-key did-you-mean suggestions. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val previous = IntArray(b.length + 1) { it }
        val currentRow = IntArray(b.length + 1)
        for (i in 1..a.length) {
            currentRow[0] = i
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(currentRow[j - 1] + 1, previous[j] + 1, previous[j - 1] + substitutionCost)
            }
            currentRow.copyInto(previous)
        }
        return previous[b.length]
    }

    private val envPattern = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")

    /**
     * Substitutes `${VAR}` / `${VAR:-default}` references in config string values with environment
     * variables. An unset variable with no default is left unchanged so the placeholder is visible.
     */
    private fun substituteEnv(value: String): String = envPattern.replace(value) { match ->
        val name = match.groupValues[1]
        val default = match.groups[2]?.value
        System.getenv(name) ?: default ?: match.value
    }

    /**
     * Serialises [instance] to a YAML-ready map.
     *
     * When [omitDefaults] is true (used for NESTED data classes), null values and empty
     * collections are dropped so generated files stay lean — e.g. an item with only a material and
     * name no longer prints `lore: []`, `enchantments: {}`, `item: null`, … On reload, absent keys
     * simply fall back to the constructor default, so this is round-trip safe. Top-level config
     * sections are always kept (omitDefaults=false) so every option stays discoverable.
     */
    private fun toMap(instance: Any, omitDefaults: Boolean = false): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val klass = instance::class
        for (param in klass.primaryConstructor?.parameters ?: emptyList()) {
            val member = klass.members.firstOrNull { it.name == param.name } ?: continue
            val value = member.call(instance)
            if (omitDefaults && isOmittable(value)) continue
            map[camelToKebab(param.name!!)] = toYamlValue(value)
        }
        return map
    }

    private fun isOmittable(value: Any?): Boolean = value == null ||
        (value is Collection<*> && value.isEmpty()) ||
        (value is Map<*, *> && value.isEmpty())

    private fun toYamlValue(value: Any?): Any? = when (value) {
        null -> null

        is List<*> -> value.map { toYamlValue(it) }

        is Map<*, *> ->
            value
                .mapKeys { it.key.toString() }
                .mapValues { (_, v) -> toYamlValue(v) }

        is Enum<*> -> value.name

        is UUID -> value.toString()

        else -> if (value::class.isData) toMap(value, omitDefaults = true) else value
    }

    private fun <T : Any> createDefault(klass: KClass<T>): T {
        val constructor = requireNotNull(klass.primaryConstructor)
        return constructor.callBy(emptyMap())
    }

    private fun validateParam(param: KParameter, value: Any?, violations: MutableList<String>) {
        val annotations = param.annotations
        for (annotation in annotations) {
            when (annotation) {
                is Min -> {
                    val num = (value as? Number)?.toLong()
                    if (num != null && num < annotation.value) {
                        violations += "${param.name} must be >= ${annotation.value} (was $num)"
                    }
                }

                is Max -> {
                    val num = (value as? Number)?.toLong()
                    if (num != null && num > annotation.value) {
                        violations += "${param.name} must be <= ${annotation.value} (was $num)"
                    }
                }

                is NotEmpty -> {
                    if (value is String && value.isBlank()) {
                        violations += "${param.name} must not be empty"
                    }
                }
            }
        }
    }

    /** Converts `camelCase` to `kebab-case`. */
    public fun camelToKebab(name: String): String = name.replace(Regex("([A-Z])")) { "-${it.value.lowercase()}" }
}
