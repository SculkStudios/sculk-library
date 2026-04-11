package gg.sculk.config.yaml

import gg.sculk.config.annotation.Max
import gg.sculk.config.annotation.Min
import gg.sculk.config.annotation.NotEmpty
import gg.sculk.core.annotation.SculkInternal
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.StringWriter
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

/**
 * Internal YAML mapper: reads/writes data class instances from/to YAML files.
 *
 * Supports: String, Int, Long, Double, Float, Boolean, and nested data classes.
 * Keys are kebab-case in YAML, camelCase in Kotlin.
 */
@SculkInternal
public object YamlMapper {
    private val yaml: Yaml by lazy {
        val opts =
            DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
                indent = 2
            }
        Yaml(opts)
    }

    /** Reads [file] and maps it to an instance of [klass]. Missing keys use constructor defaults. */
    public fun <T : Any> load(
        file: File,
        klass: KClass<T>,
    ): T {
        val raw: Map<String, Any?> =
            if (file.exists() && file.length() > 0) {
                @Suppress("UNCHECKED_CAST")
                yaml.load(file.readText()) as? Map<String, Any?> ?: emptyMap()
            } else {
                emptyMap()
            }
        return fromMap(raw, klass)
    }

    /** Writes [instance] to [file] as YAML, creating parent directories if needed. */
    public fun <T : Any> save(
        file: File,
        instance: T,
    ) {
        file.parentFile?.mkdirs()
        val map = toMap(instance)
        val writer = StringWriter()
        yaml.dump(map, writer)
        file.writeText(writer.toString())
    }

    /** Writes defaults for [klass] to [file] only for keys not already present. */
    public fun <T : Any> writeDefaults(
        file: File,
        klass: KClass<T>,
    ) {
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
        val writer = StringWriter()
        yaml.dump(merged, writer)
        file.writeText(writer.toString())
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

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private fun <T : Any> fromMap(
        map: Map<String, Any?>,
        klass: KClass<T>,
    ): T {
        val constructor =
            requireNotNull(klass.primaryConstructor) {
                "Config class ${klass.simpleName} must have a primary constructor."
            }
        val args = mutableMapOf<KParameter, Any?>()
        for (param in constructor.parameters) {
            val key = camelToKebab(param.name!!)
            val raw = map[key]
            if (raw != null) {
                args[param] = coerce(raw, param)
            }
            // Missing keys keep the constructor default — Kotlin handles this automatically.
        }
        return constructor.callBy(args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerce(
        value: Any,
        param: KParameter,
    ): Any? {
        val type = param.type.classifier as? KClass<*> ?: return value
        return when (type) {
            Int::class -> (value as? Number)?.toInt() ?: value
            Long::class -> (value as? Number)?.toLong() ?: value
            Double::class -> (value as? Number)?.toDouble() ?: value
            Float::class -> (value as? Number)?.toFloat() ?: value
            Boolean::class -> value
            String::class -> value.toString()
            else -> if (value is Map<*, *>) fromMap(value as Map<String, Any?>, type) else value
        }
    }

    private fun toMap(instance: Any): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val klass = instance::class
        for (param in klass.primaryConstructor?.parameters ?: emptyList()) {
            val member = klass.members.firstOrNull { it.name == param.name } ?: continue
            val value = member.call(instance)
            val key = camelToKebab(param.name!!)
            map[key] = if (value != null && value::class.isData) toMap(value) else value
        }
        return map
    }

    private fun <T : Any> createDefault(klass: KClass<T>): T {
        val constructor = requireNotNull(klass.primaryConstructor)
        return constructor.callBy(emptyMap())
    }

    private fun validateParam(
        param: KParameter,
        value: Any?,
        violations: MutableList<String>,
    ) {
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
