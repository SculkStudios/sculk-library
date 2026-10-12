package studio.sculk.config

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.snakeyaml.engine.v2.comments.CommentLine
import org.snakeyaml.engine.v2.comments.CommentType
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import java.util.Optional

/** Encodes a value into a node tree with `@Comment` text on its keys. Defaults are always written. */
internal object YamlEncoding {
    fun <T> encode(serializer: SerializationStrategy<T>, value: T): Node {
        var result: Node? = null
        serializer.serialize(RootEncoder { result = it }, value)
        return checkNotNull(result) { "${serializer.descriptor.serialName} encoded to nothing." }
    }

    fun scalar(text: String, tag: Tag = Tag.STR): ScalarNode = ScalarNode(tag, text, ScalarStyle.PLAIN)

    fun comments(lines: List<String>, blankLineFirst: Boolean = false): List<CommentLine> = buildList {
        if (blankLineFirst) add(CommentLine(Optional.empty(), Optional.empty(), "", CommentType.BLANK_LINE))
        lines.flatMap { it.lines() }.forEach { add(CommentLine(Optional.empty(), Optional.empty(), " $it", CommentType.BLOCK)) }
    }
}

/** Encodes one value and hands the resulting node to [put]. */
internal open class NodeEncoder(private val put: (Node) -> Unit) : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    protected open fun place(node: Node) = put(node)

    override fun encodeString(value: String) = place(YamlEncoding.scalar(value))

    override fun encodeBoolean(value: Boolean) = place(YamlEncoding.scalar(value.toString(), Tag.BOOL))

    override fun encodeLong(value: Long) = place(YamlEncoding.scalar(value.toString(), Tag.INT))

    override fun encodeInt(value: Int) = encodeLong(value.toLong())

    override fun encodeShort(value: Short) = encodeLong(value.toLong())

    override fun encodeByte(value: Byte) = encodeLong(value.toLong())

    override fun encodeDouble(value: Double) = place(YamlEncoding.scalar(value.toString(), Tag.FLOAT))

    override fun encodeFloat(value: Float) = encodeDouble(value.toDouble())

    override fun encodeChar(value: Char) = encodeString(value.toString())

    override fun encodeNull() = place(YamlEncoding.scalar("null", Tag.NULL))

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        encodeString(kebab(enumDescriptor.getElementName(index)).lowercase())

    override fun encodeInline(descriptor: SerialDescriptor): AbstractEncoder = this

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = when (descriptor.kind) {
        StructureKind.LIST -> SequenceEncoder(::place)
        StructureKind.MAP -> MapEncoder(::place)
        is PolymorphicKind -> PolymorphicEncoder(::place)
        else -> MappingEncoder(::place, topLevel = this is RootMarker)
    }
}

/** Marks the encoder for the whole file, whose keys get a blank line between commented sections. */
internal interface RootMarker

internal class RootEncoder(put: (Node) -> Unit) :
    NodeEncoder(put),
    RootMarker

internal class MappingEncoder(private val done: (Node) -> Unit, private val topLevel: Boolean) : NodeEncoder({}) {
    private val tuples = mutableListOf<NodeTuple>()
    private var key: ScalarNode? = null

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val lines = descriptor.getElementAnnotations(index).filterIsInstance<Comment>().flatMap { it.lines.asList() }
        key = YamlEncoding.scalar(kebab(descriptor.getElementName(index))).apply {
            if (lines.isNotEmpty()) blockComments = YamlEncoding.comments(lines, blankLineFirst = topLevel && tuples.isNotEmpty())
        }
        return true
    }

    override fun place(node: Node) {
        tuples += NodeTuple(checkNotNull(key) { "A value was encoded before its key." }, node)
    }

    override fun endStructure(descriptor: SerialDescriptor) = done(MappingNode(Tag.MAP, tuples, FlowStyle.BLOCK))
}

internal class SequenceEncoder(private val done: (Node) -> Unit) : NodeEncoder({}) {
    private val items = mutableListOf<Node>()

    override fun place(node: Node) {
        items += node
    }

    override fun endStructure(descriptor: SerialDescriptor) = done(SequenceNode(Tag.SEQ, items, FlowStyle.BLOCK))
}

internal class MapEncoder(private val done: (Node) -> Unit) : NodeEncoder({}) {
    private val tuples = mutableListOf<NodeTuple>()
    private var key: Node? = null

    override fun place(node: Node) {
        val pending = key
        if (pending == null) {
            key = node
        } else {
            tuples += NodeTuple(pending, node)
            key = null
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) = done(MappingNode(Tag.MAP, tuples, FlowStyle.BLOCK))
}

/** Writes a polymorphic value as its section with a leading `type` key. */
internal class PolymorphicEncoder(private val done: (Node) -> Unit) : NodeEncoder({}) {
    private var type: String? = null

    override fun encodeString(value: String) {
        type = typeName(value)
    }

    override fun <T> encodeSerializableElement(descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T) {
        val section =
            YamlEncoding.encode(serializer, value) as? MappingNode ?: error("A polymorphic config value must encode to a section.")
        val typeTuple = NodeTuple(YamlEncoding.scalar(NodeDecoder.TYPE_KEY), YamlEncoding.scalar(checkNotNull(type)))
        done(MappingNode(Tag.MAP, mutableListOf(typeTuple).apply { addAll(section.value) }, FlowStyle.BLOCK))
    }

    override fun endStructure(descriptor: SerialDescriptor) = Unit
}
