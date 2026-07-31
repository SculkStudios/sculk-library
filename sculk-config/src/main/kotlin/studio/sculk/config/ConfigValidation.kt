package studio.sculk.config

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind

/**
 * Checks `@Min` / `@Max` / `@NotEmpty` against the parsed YAML tree.
 *
 * Validating the tree rather than the decoded object is what makes a violation reportable as
 * `storage.mysql.port` instead of as `port`. Reflection over the decoded instance would know the
 * value but not where in the file it came from, and "port must be at most 65535" in a file with
 * three ports in it is not an actionable message.
 *
 * Violations are returned, never thrown. Refusing to boot over one out-of-range number is a worse
 * outcome for a live server than starting with it and saying so in the log.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object ConfigValidation {
    fun violations(node: YamlNode, descriptor: SerialDescriptor): List<String> {
        val found = mutableListOf<String>()
        walk(node, descriptor, prefix = "", into = found, seen = HashSet())
        return found
    }

    private fun walk(node: YamlNode, descriptor: SerialDescriptor, prefix: String, into: MutableList<String>, seen: MutableSet<String>) {
        if (node !is YamlMap) return
        if (descriptor.kind != StructureKind.CLASS && descriptor.kind != StructureKind.OBJECT) return
        if (!seen.add(descriptor.serialName + "@" + prefix)) return

        for (index in 0 until descriptor.elementsCount) {
            val name = yamlKey(descriptor.getElementName(index))
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            val value = node.entries.entries.firstOrNull { it.key.content == name }?.value ?: continue

            if (value is YamlScalar) {
                check(value, descriptor.getElementAnnotations(index), path, into)
            } else {
                walk(value, descriptor.getElementDescriptor(index), path, into, seen)
            }
        }
    }

    private fun check(scalar: YamlScalar, annotations: List<Annotation>, path: String, into: MutableList<String>) {
        val number = scalar.content.toDoubleOrNull()
        for (annotation in annotations) {
            when (annotation) {
                is Min -> if (number != null && number < annotation.value) {
                    into += "$path is ${scalar.content}, which is below the minimum of ${annotation.value}"
                }

                is Max -> if (number != null && number > annotation.value) {
                    into += "$path is ${scalar.content}, which is above the maximum of ${annotation.value}"
                }

                is NotEmpty -> if (scalar.content.isBlank()) {
                    into += "$path is empty"
                }

                else -> Unit
            }
        }
    }
}
