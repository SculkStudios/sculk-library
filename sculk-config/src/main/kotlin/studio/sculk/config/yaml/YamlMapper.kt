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
 * Keys are kebab-case in YAML, camelCase in Kotlin.
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

    /** Reads [file] and maps it to an instance of [klass]. Missing keys use constructor defaults. */
    public fun <T : Any> load(file: File, klass: KClass<T>): T {
        val raw = loadRaw(file)
        return fromMap(raw, klass)
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

    private fun <T : Any> fromMap(map: Map<String, Any?>, klass: KClass<T>): T {
        val constructor =
            requireNotNull(klass.primaryConstructor) {
                "Config class ${klass.simpleName} must have a primary constructor."
            }
        val args = mutableMapOf<KParameter, Any?>()
        for (param in constructor.parameters) {
            val key = camelToKebab(param.name!!)
            val raw = map[key]
            if (raw != null) {
                args[param] = coerce(raw, param.type)
            }
            // Missing keys keep the constructor default — Kotlin handles this automatically.
        }
        return constructor.callBy(args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerce(value: Any?, type: KType): Any? {
        if (value == null) return null

        // List<T>
        if (type.classifier == List::class) {
            val elementType = type.arguments.firstOrNull()?.type ?: return value
            return (value as? List<*>)?.map { coerce(it, elementType) } ?: emptyList<Any?>()
        }

        // Map<String, V>
        if (type.classifier == Map::class) {
            val valueType = type.arguments.getOrNull(1)?.type ?: return value
            return (value as? Map<*, *>)?.mapValues { (_, v) -> coerce(v, valueType) } ?: emptyMap<String, Any?>()
        }

        val klass = type.classifier as? KClass<*> ?: return value

        // UUID
        if (klass == UUID::class) {
            return runCatching { UUID.fromString(value.toString()) }.getOrNull() ?: value
        }

        // Enum
        if (klass.java.isEnum) {
            @Suppress("UNCHECKED_CAST")
            val enumClass = klass.java as Class<out Enum<*>>
            return enumClass.enumConstants
                .firstOrNull { it.name.equals(value.toString(), ignoreCase = true) }
                ?: value
        }

        return when (klass) {
            Int::class -> (value as? Number)?.toInt() ?: value
            Long::class -> (value as? Number)?.toLong() ?: value
            Double::class -> (value as? Number)?.toDouble() ?: value
            Float::class -> (value as? Number)?.toFloat() ?: value
            Boolean::class -> value
            String::class -> substituteEnv(value.toString())
            else -> if (value is Map<*, *>) fromMap(value as Map<String, Any?>, klass) else value
        }
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
