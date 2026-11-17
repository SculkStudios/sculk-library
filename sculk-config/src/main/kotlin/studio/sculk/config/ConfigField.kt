package studio.sculk.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import studio.sculk.annotation.SculkStable

/**
 * One setting of a configuration that an editor can change: a scalar at [path] (`shop.max-items`),
 * with what the file's comments say about it, its bounds, and its [current] value as the file
 * writes it. A secret's value is never shown: [current] is empty for it.
 */
@SculkStable
public class ConfigField internal constructor(
    public val path: String,
    public val kind: Kind,
    comment: List<String>,
    /** The bounds from `@Range` or `@DecimalRange`, if any. */
    public val range: ClosedFloatingPointRange<Double>?,
    choices: List<String>,
    public val notBlank: Boolean,
    public val secret: Boolean,
    public val current: String,
) {
    public val comment: List<String> = comment.toList()

    /** For [Kind.CHOICE], the values the file accepts. */
    public val choices: List<String> = choices.toList()

    @SculkStable
    public enum class Kind { TEXT, TOGGLE, WHOLE, DECIMAL, CHOICE }

    override fun toString(): String = "ConfigField($path, $kind, ${if (secret) "secret" else current})"
}

/**
 * Lists the scalar settings of a configuration class, depth first, and reads their values from an
 * encoded tree. Lists, maps and sealed choices are not scalars and are left to the file.
 */
internal object ConfigFields {
    fun <T : Any> of(serializer: KSerializer<T>, encoded: Node): List<ConfigField> =
        buildList { walk(serializer.descriptor, "", encoded, this) }

    private fun walk(descriptor: SerialDescriptor, prefix: String, encoded: Node, into: MutableList<ConfigField>) {
        for (index in 0 until descriptor.elementsCount) {
            val element = descriptor.getElementDescriptor(index)
            val path = child(prefix, kebab(descriptor.getElementName(index)))
            val annotations = descriptor.getElementAnnotations(index)
            if (element.kind == StructureKind.CLASS && element.serialName != SecretSerializer.descriptor.serialName) {
                walk(element, path, encoded, into)
                continue
            }
            val secret = element.serialName == SecretSerializer.descriptor.serialName
            val kind = when (element.kind) {
                PrimitiveKind.BOOLEAN -> ConfigField.Kind.TOGGLE
                PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> ConfigField.Kind.WHOLE
                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> ConfigField.Kind.DECIMAL
                PrimitiveKind.STRING, PrimitiveKind.CHAR -> ConfigField.Kind.TEXT
                SerialKind.ENUM -> ConfigField.Kind.CHOICE
                else -> if (secret) ConfigField.Kind.TEXT else continue
            }
            val range = annotations.firstNotNullOfOrNull { annotation ->
                when (annotation) {
                    is Range -> annotation.min.toDouble()..annotation.max.toDouble()
                    is DecimalRange -> annotation.min..annotation.max
                    else -> null
                }
            }
            val choices = if (kind == ConfigField.Kind.CHOICE) {
                (0 until element.elementsCount).map { enumName(element.getElementName(it)) }
            } else {
                emptyList()
            }
            val current = if (secret) "" else ((YamlTree.find(encoded, path) as? ScalarNode)?.value ?: "")
            into += ConfigField(
                path,
                kind,
                annotations.filterIsInstance<Comment>().flatMap { it.lines.toList() },
                range,
                choices,
                annotations.any { it is NotBlank },
                secret,
                current,
            )
        }
    }
}
