package studio.sculk.config

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag

/** What every decoder in one decode shares: the file name for errors and how to read the environment. */
internal class DecodeContext(val file: String, val environment: (String) -> String?)

/**
 * Decodes kotlinx-serializable values from a snakeyaml-engine node tree. Every error names the
 * dotted path and the line, and a value typed as a [studio.sculk.Secret] is never echoed back.
 */
@Suppress("TooManyFunctions")
internal abstract class NodeDecoder(protected val context: DecodeContext) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    protected abstract val node: Node?
    protected abstract val path: String
    protected open val annotations: List<Annotation> get() = emptyList()
    protected var secret: Boolean = false

    protected fun fail(expected: String, received: String, at: Node? = node, where: String = path, hint: String? = null): Nothing =
        throw ConfigDecodingException(InvalidConfig(context.file, where, at?.line(), expected, received, hint))

    private fun scalar(expected: String): String {
        val current = node ?: fail(expected, "nothing", where = path)
        if (current !is ScalarNode) fail(expected, describe(current))
        return resolveEnvironment(current.value)
    }

    protected fun received(text: String): String = if (secret) studio.sculk.Secret.REDACTED else "'$text'"

    private fun resolveEnvironment(text: String): String = ENVIRONMENT.replace(text) { match ->
        val name = match.groupValues[1]
        val fallback = match.groups[2]?.value
        context.environment(name) ?: fallback
            ?: fail("environment variable $name to be set", "it unset", hint = "Set $name or give a default: \${$name:-value}.")
    }

    override fun decodeNotNullMark(): Boolean {
        val current = node ?: return false
        return !(current is ScalarNode && (current.tag == Tag.NULL || (current.isPlain && current.value in NULLS)))
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeString(): String {
        val text = scalar("text")
        if (annotations.any { it is NotBlank } && text.isBlank()) fail("text that is not blank", received(text))
        return text
    }

    override fun decodeBoolean(): Boolean {
        val text = scalar("true or false")
        return when (text.lowercase()) {
            "true", "yes", "on" -> true
            "false", "no", "off" -> false
            else -> fail("true or false", received(text))
        }
    }

    override fun decodeLong(): Long {
        val range = annotations.filterIsInstance<Range>().firstOrNull()
        val expected = range?.let { "an integer between ${it.min} and ${it.max}" } ?: "an integer"
        val text = scalar(expected)
        val value = text.replace("_", "").toLongOrNull() ?: fail(expected, received(text))
        if (range != null && value !in range.min..range.max) fail(expected, received(text).trim('\''))
        return value
    }

    override fun decodeInt(): Int = decodeBounded(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    override fun decodeShort(): Short = decodeBounded(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()

    override fun decodeByte(): Byte = decodeBounded(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()).toByte()

    private fun decodeBounded(min: Long, max: Long): Long {
        val value = decodeLong()
        if (value !in min..max) fail("an integer between $min and $max", received(value.toString()).trim('\''))
        return value
    }

    override fun decodeDouble(): Double {
        val range = annotations.filterIsInstance<DecimalRange>().firstOrNull()
        val expected = range?.let { "a number between ${it.min} and ${it.max}" } ?: "a number"
        val text = scalar(expected)
        val value = text.replace("_", "").toDoubleOrNull() ?: fail(expected, received(text))
        if (range != null && value !in range.min..range.max) fail(expected, received(text).trim('\''))
        return value
    }

    override fun decodeFloat(): Float = decodeDouble().toFloat()

    override fun decodeChar(): Char {
        val text = scalar("a single character")
        return text.singleOrNull() ?: fail("a single character", received(text))
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val names = enumDescriptor.elementNames.toList()
        val expected = "one of ${names.joinToString { kebab(it) }}"
        val text = scalar(expected)
        val wanted = normalise(text)
        return names.indexOfFirst { normalise(it) == wanted }.takeIf { it >= 0 } ?: fail(expected, received(text))
    }

    override fun decodeInline(descriptor: SerialDescriptor): AbstractDecoder = this

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        secret = deserializer.descriptor.serialName == SECRET
        return deserializer.deserialize(this)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val current = node
        return when (descriptor.kind) {
            StructureKind.LIST -> SequenceDecoder(
                context,
                current as? SequenceNode ?: fail("a list", current?.let(::describe) ?: "nothing"),
                path,
            )

            StructureKind.MAP -> MapDecoder(
                context,
                current as? MappingNode ?: fail("a section of keys", current?.let(::describe) ?: "nothing"),
                path,
            )

            is PolymorphicKind -> PolymorphicDecoder(
                context,
                current as? MappingNode ?: fail("a section with a 'type' key", current?.let(::describe) ?: "nothing"),
                path,
                descriptor,
            )

            else -> MappingDecoder(context, current as? MappingNode ?: emptyIfMissing(current), path, descriptor, ignoredKeys())
        }
    }

    protected open fun ignoredKeys(): Set<String> = emptySet()

    private fun emptyIfMissing(current: Node?): MappingNode {
        if (current != null && !(current is ScalarNode && current.value.isEmpty())) fail("a section of keys", describe(current))
        return MappingNode(Tag.MAP, mutableListOf(), org.snakeyaml.engine.v2.common.FlowStyle.BLOCK)
    }

    companion object {
        const val TYPE_KEY = "type"
        const val SECRET = "studio.sculk.Secret"
        private val NULLS = setOf("", "~", "null", "Null", "NULL")
        private val ENVIRONMENT = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}""")

        fun normalise(text: String): String = text.lowercase().replace("-", "").replace("_", "")

        fun describe(node: Node): String = when (node) {
            is ScalarNode -> "'${node.value}'"
            is SequenceNode -> "a list"
            is MappingNode -> "a section"
            else -> "a ${node.nodeType.name.lowercase()}"
        }
    }
}

/** 1-based line of a node, for messages. */
internal fun Node.line(): Int? = startMark.map { it.line + 1 }.orElse(null)

/** Decodes the single value at [node]. */
internal class ValueDecoder(
    context: DecodeContext,
    override val node: Node?,
    override val path: String,
    private val ignoredKeys: Set<String> = emptySet(),
    override val annotations: List<Annotation> = emptyList(),
) : NodeDecoder(context) {
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = error("A single value has no elements.")

    override fun ignoredKeys(): Set<String> = ignoredKeys
}

/** Decodes a class from a mapping, matching kebab-case keys; unknown keys are left for the file. */
internal class MappingDecoder(
    context: DecodeContext,
    private val mapping: MappingNode,
    private val basePath: String,
    descriptor: SerialDescriptor,
    ignoredKeys: Set<String>,
) : NodeDecoder(context) {
    private val entries: Map<String, Node> = mapping.value
        .mapNotNull { tuple -> (tuple.keyNode as? ScalarNode)?.let { it.value to tuple.valueNode } }
        .filter { it.first !in ignoredKeys }
        .toMap()
    private val keys = (0 until descriptor.elementsCount).map { kebab(descriptor.getElementName(it)) }
    private var index = -1
    override var node: Node? = null
    override var path: String = basePath
    override var annotations: List<Annotation> = emptyList()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (++index < descriptor.elementsCount) {
            val value = entries[keys[index]]
            if (value != null || !descriptor.isElementOptional(index)) {
                secret = false
                node = value
                path = child(basePath, keys[index])
                annotations = descriptor.getElementAnnotations(index)
                if (value == null && !descriptor.getElementDescriptor(index).isNullable) {
                    fail("a value for ${keys[index]}", "nothing", at = mapping, hint = "Add '${keys[index]}' to the file.")
                }
                return index
            }
        }
        return CompositeDecoder.DECODE_DONE
    }
}

/** Decodes a list from a sequence. */
internal class SequenceDecoder(context: DecodeContext, private val sequence: SequenceNode, private val basePath: String) :
    NodeDecoder(context) {
    private var index = -1
    override var node: Node? = null
    override var path: String = basePath

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = sequence.value.size

    override fun decodeSequentially(): Boolean = false

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (++index >= sequence.value.size) return CompositeDecoder.DECODE_DONE
        node = sequence.value[index]
        path = "$basePath[$index]"
        return index
    }
}

/** Decodes a map from a mapping: even indices are keys, odd are values. */
internal class MapDecoder(context: DecodeContext, private val mapping: MappingNode, private val basePath: String) : NodeDecoder(context) {
    private var index = -1
    override var node: Node? = null
    override var path: String = basePath

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (++index >= mapping.value.size * 2) return CompositeDecoder.DECODE_DONE
        val tuple = mapping.value[index / 2]
        val key = (tuple.keyNode as? ScalarNode)?.value ?: "?"
        node = if (index % 2 == 0) tuple.keyNode else tuple.valueNode
        path = if (index % 2 == 0) basePath else child(basePath, key)
        return index
    }
}

/**
 * Decodes a polymorphic value through kotlinx's public protocol: element 0 is the type, element 1
 * the value. The type comes from the section's `type` key and may be written as the subtype's
 * simple name in kebab case (`mysql` for `Storage.MySql`).
 */
internal class PolymorphicDecoder(
    context: DecodeContext,
    private val mapping: MappingNode,
    private val basePath: String,
    descriptor: SerialDescriptor,
) : NodeDecoder(context) {
    private val subtypes = descriptor.getElementDescriptor(1).elementNames.toList()
    private val typeNode = mapping.value.firstOrNull { (it.keyNode as? ScalarNode)?.value == TYPE_KEY }?.valueNode
    private var index = -1
    override var node: Node? = null
    override var path: String = basePath

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        secret = false
        when (index) {
            0 -> {
                node = typeNode
                path = child(basePath, TYPE_KEY)
            }

            1 -> {
                node = mapping
                path = basePath
            }

            else -> return CompositeDecoder.DECODE_DONE
        }
        return index
    }

    override fun decodeString(): String {
        if (index != 0) return super.decodeString()
        val expected = "type to be one of ${subtypes.joinToString { typeName(it) }}"
        val requested = (typeNode as? ScalarNode)?.value ?: fail(expected, "no type", at = mapping, where = basePath)
        if (subtypes.isEmpty()) return requested
        return subtypes.firstOrNull { it == requested || normalise(typeName(it)) == normalise(requested) } ?: fail(expected, "'$requested'")
    }

    override fun ignoredKeys(): Set<String> = if (index == 1) setOf(TYPE_KEY) else emptySet()
}
