package studio.sculk.config

import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.StreamDataWriter
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag

/** Parsing, printing and merging node trees, comments included. */
internal object YamlTree {
    private val load = LoadSettings.builder().setParseComments(true).build()
    private val dump = DumpSettings.builder()
        .setDumpComments(true)
        .setDefaultFlowStyle(FlowStyle.BLOCK)
        .setIndent(2)
        .setIndicatorIndent(2)
        .setIndentWithIndicator(true)
        .setWidth(LINE_WIDTH)
        .build()

    fun parse(text: String): MappingNode {
        val root = Compose(load).composeString(text).orElse(null) ?: return emptyMapping()
        return root as? MappingNode
            ?: throw ConfigDecodingException(
                InvalidConfig("", "", root.line(), "a section of keys at the top of the file", NodeDecoder.describe(root)),
            )
    }

    fun print(node: Node): String {
        val out = StringBuilder()
        Dump(dump).dumpNode(
            node,
            object : StreamDataWriter {
                override fun write(str: String) {
                    out.append(str)
                }

                override fun write(str: String, off: Int, len: Int) {
                    out.append(str, off, off + len)
                }
            },
        )
        return out.toString()
    }

    fun emptyMapping(): MappingNode = MappingNode(Tag.MAP, mutableListOf(), FlowStyle.BLOCK)

    fun keyOf(tuple: NodeTuple): String? = (tuple.keyNode as? ScalarNode)?.value

    fun get(mapping: MappingNode, key: String): Node? = mapping.value.firstOrNull { keyOf(it) == key }?.valueNode

    /** The node at a dotted path such as `storage.hosts[1].port`, or null. */
    fun find(root: Node, path: String): Node? {
        if (path.isEmpty()) return root
        var current: Node? = root
        for (segment in path.split('.')) {
            val name = segment.substringBefore('[')
            current = (current as? MappingNode)?.let { get(it, name) }
            INDEX.findAll(segment).forEach { match -> current = (current as? SequenceNode)?.value?.getOrNull(match.groupValues[1].toInt()) }
        }
        return current
    }

    /**
     * Adds every key [complete] has and [existing] lacks, recursing into sections both have, and
     * never touching what is already there. A map's entries are the operator's, so a map that
     * exists is never extended.
     */
    fun fillMissing(existing: MappingNode, complete: MappingNode) {
        for (tuple in complete.value) {
            val key = keyOf(tuple) ?: continue
            val present = existing.value.firstOrNull { keyOf(it) == key }?.valueNode
            val section = tuple.valueNode
            when {
                present == null -> existing.value.add(tuple)

                present is MappingNode && section is MappingNode && !YamlEncoding.isMap(section) && sameType(present, section) ->
                    fillMissing(present, section)
            }
        }
    }

    private fun sameType(a: Node, b: Node): Boolean {
        val typeA = (a as? MappingNode)?.let { get(it, NodeDecoder.TYPE_KEY) as? ScalarNode }?.value
        val typeB = (b as? MappingNode)?.let { get(it, NodeDecoder.TYPE_KEY) as? ScalarNode }?.value
        return typeA == typeB
    }

    /**
     * Writes [generated]'s values into [existing], keeping the operator's comments, key order and
     * unknown keys. A map takes exactly [generated]'s entries, keeping the comments of those that
     * remain. A scalar whose resolved text already equals the new value is left exactly as written,
     * so `${DATABASE_PASSWORD}` is never replaced by the secret it resolves to.
     */
    fun merge(existing: Node, generated: Node, resolve: (String) -> String): Node = when {
        existing is MappingNode && generated is MappingNode && YamlEncoding.isMap(generated) -> existing.apply {
            val previous = value.toList()
            value.clear()
            generated.value.mapTo(value) { entry ->
                val key = keyOf(entry)?.let(resolve)
                val kept = previous.firstOrNull { keyOf(it)?.let(resolve) == key }
                if (kept == null) entry else NodeTuple(kept.keyNode, merge(kept.valueNode, entry.valueNode, resolve))
            }
        }

        existing is MappingNode && generated is MappingNode -> existing.apply {
            for (tuple in generated.value) {
                val key = keyOf(tuple) ?: continue
                val index = value.indexOfFirst { keyOf(it) == key }
                if (index < 0) {
                    value.add(tuple)
                } else {
                    value[index] = NodeTuple(value[index].keyNode, merge(value[index].valueNode, tuple.valueNode, resolve))
                }
            }
        }

        existing is ScalarNode && generated is ScalarNode && resolve(existing.value) == resolve(generated.value) -> existing

        else -> generated.also { copyComments(existing, it) }
    }

    private fun copyComments(from: Node, to: Node) {
        to.blockComments = from.blockComments
        to.inLineComments = from.inLineComments
        to.endComments = from.endComments
    }

    /** Moves the value at [from] to [to], creating sections on the way. Returns false when [from] does not exist. */
    fun move(root: MappingNode, from: String, to: String): Boolean {
        val tuple = detach(root, from) ?: return false
        val (parent, key) = parentOf(root, to, create = true) ?: return false
        parent.value.removeIf { keyOf(it) == key }
        parent.value.add(
            NodeTuple(
                ScalarNode(Tag.STR, key, (tuple.keyNode as ScalarNode).scalarStyle).also {
                    copyComments(tuple.keyNode, it)
                },
                tuple.valueNode,
            ),
        )
        return true
    }

    fun remove(root: MappingNode, path: String): Boolean = detach(root, path) != null

    fun set(root: MappingNode, path: String, value: Node) {
        val (parent, key) = checkNotNull(parentOf(root, path, create = true)) { "Cannot set $path: a parent is not a section." }
        val index = parent.value.indexOfFirst { keyOf(it) == key }
        val tuple = NodeTuple(YamlEncoding.scalar(key), value)
        if (index < 0) parent.value.add(tuple) else parent.value[index] = NodeTuple(parent.value[index].keyNode, value)
    }

    private fun detach(root: MappingNode, path: String): NodeTuple? {
        val (parent, key) = parentOf(root, path, create = false) ?: return null
        val index = parent.value.indexOfFirst { keyOf(it) == key }
        return if (index < 0) null else parent.value.removeAt(index)
    }

    private fun parentOf(root: MappingNode, path: String, create: Boolean): Pair<MappingNode, String>? {
        val segments = path.split('.')
        var current = root
        for (segment in segments.dropLast(1)) {
            val next = get(current, segment)
            current = when {
                next is MappingNode -> next
                next == null && create -> emptyMapping().also { current.value.add(NodeTuple(YamlEncoding.scalar(segment), it)) }
                else -> return null
            }
        }
        return current to segments.last()
    }

    private const val LINE_WIDTH = 120
    private val INDEX = Regex("""\[(\d+)]""")
}
