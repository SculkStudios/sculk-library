package studio.sculk.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

private val KEY_LINE = Regex("^(\\s*)([^\\s#:][^:]*):")

/**
 * Puts `@Comment` text back into rendered YAML.
 *
 * kaml emits data, not documentation, so comments are injected in a second pass. They are matched
 * to keys **by path** (`storage.mysql.host`) rather than by name, because a name alone is ambiguous
 * the moment two sections both have a `host`, and matching on the top level only is why comments
 * on nested settings silently disappeared before.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object CommentedYaml {
    /** Renders [yaml] with the comments declared on [descriptor], plus the file header. */
    fun decorate(yaml: String, descriptor: SerialDescriptor, revision: Int): String {
        val comments = collect(descriptor, prefix = "", into = LinkedHashMap(), seen = HashSet())
        val header = commentLines(descriptor.annotations)

        val out = StringBuilder()
        header.forEach { out.append("# ").append(it).append('\n') }
        // The revision marker is a comment so it is never a property the class has to declare.
        if (revision > 1) out.append("# revision: ").append(revision).append('\n')
        if (out.isNotEmpty()) out.append('\n')

        val path = ArrayDeque<Pair<Int, String>>()
        var previousWasBlank = true
        var previousIndent = -1

        for (line in yaml.lines()) {
            val match = KEY_LINE.find(line)
            if (match == null) {
                out.append(line).append('\n')
                previousWasBlank = line.isBlank()
                continue
            }

            val indent = match.groupValues[1].length
            val key = match.groupValues[2].trim()
            while (path.isNotEmpty() && path.last().first >= indent) path.removeLast()
            path.addLast(indent to key)

            val comment = comments[path.joinToString(".") { it.second }]
            if (comment != null) {
                // A blank line before each documented key, or the file reads as one wall of text
                // and a server owner cannot see where a setting starts. Not for the first key
                // inside a section, though — a gap immediately under `mysql:` reads as if the
                // section were empty.
                if (!previousWasBlank && indent <= previousIndent) out.append('\n')
                comment.forEach { out.append(match.groupValues[1]).append("# ").append(it).append('\n') }
            }
            out.append(line).append('\n')
            previousWasBlank = false
            previousIndent = indent
        }

        return out.toString().trimEnd('\n') + "\n"
    }

    /**
     * The comment lines declared by [annotations], with embedded newlines split out.
     *
     * `@Comment` is vararg, so one line per argument is the spelling it invites — but `@Comment("a\nb")`
     * is the one someone writes anyway, and emitting it as a single `# a\nb` puts `b` into the file as
     * a bare unprefixed line. That does not parse, and it fails with no exception and no warning at the
     * point it is written. Splitting here makes both spellings mean the same thing.
     */
    private fun commentLines(annotations: List<Annotation>): List<String> =
        annotations.filterIsInstance<Comment>().flatMap { it.lines.asList() }.flatMap { it.lineSequence() }

    private fun collect(
        descriptor: SerialDescriptor,
        prefix: String,
        into: MutableMap<String, List<String>>,
        seen: MutableSet<String>,
    ): Map<String, List<String>> {
        // A self-referencing config would otherwise recurse forever building paths nothing emits.
        if (!seen.add(descriptor.serialName + "@" + prefix)) return into
        if (descriptor.kind != StructureKind.CLASS && descriptor.kind != StructureKind.OBJECT) return into

        for (index in 0 until descriptor.elementsCount) {
            val name = yamlKey(descriptor.getElementName(index))
            val path = if (prefix.isEmpty()) name else "$prefix.$name"

            commentLines(descriptor.getElementAnnotations(index))
                .takeIf { it.isNotEmpty() }
                ?.let { into[path] = it }

            val child = descriptor.getElementDescriptor(index)
            if (child.kind == StructureKind.CLASS || child.kind == SerialKind.CONTEXTUAL) {
                collect(child, path, into, seen)
            }
        }
        return into
    }
}
