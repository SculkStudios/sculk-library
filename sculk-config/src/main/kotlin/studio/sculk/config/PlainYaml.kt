package studio.sculk.config

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar

/**
 * Converts a parsed YAML tree to plain Kotlin values and back to YAML text.
 *
 * Only exists to give [ConfigDocument] something mutable to work on: a migration renames and
 * removes keys, and kaml's node tree is immutable and carries source positions that stop making
 * sense once you edit it.
 *
 * The text this emits is never written to a file — it goes straight back into the decoder, and the
 * file on disk is always rendered by kaml from the decoded object. So its formatting does not
 * matter; only that scalars stay unquoted, because that is what lets kaml resolve `30` as an Int
 * for one property and a String for another.
 */
internal object PlainYaml {
    fun toPlain(node: YamlNode): Any? = when (node) {
        is YamlScalar -> node.content
        is YamlNull -> null
        is YamlList -> node.items.map { toPlain(it) }
        is YamlMap -> node.entries.entries.associate { (key, value) -> key.content to toPlain(value) }
        else -> null
    }

    fun emit(value: Any?): String = StringBuilder().also { write(value, it, indent = 0) }.toString()

    private fun write(value: Any?, out: StringBuilder, indent: Int) {
        val pad = " ".repeat(indent)
        when (value) {
            null -> out.append(pad).append("null\n")

            is Map<*, *> -> {
                if (value.isEmpty()) {
                    out.append(pad).append("{}\n")
                    return
                }
                for ((key, child) in value) {
                    out.append(pad).append(key).append(':')
                    if (child is Map<*, *> || child is List<*>) {
                        out.append('\n')
                        write(child, out, indent + 2)
                    } else {
                        out.append(' ').append(scalar(child)).append('\n')
                    }
                }
            }

            is List<*> -> {
                if (value.isEmpty()) {
                    out.append(pad).append("[]\n")
                    return
                }
                for (item in value) {
                    if (item is Map<*, *> || item is List<*>) {
                        out.append(pad).append("-\n")
                        write(item, out, indent + 2)
                    } else {
                        out.append(pad).append("- ").append(scalar(item)).append('\n')
                    }
                }
            }

            else -> out.append(pad).append(scalar(value)).append('\n')
        }
    }

    private fun scalar(value: Any?): String {
        val text = value?.toString() ?: return "null"
        // Quote only what would otherwise change meaning; an unquoted empty string vanishes, and a
        // leading indicator character makes the line a different YAML construct entirely.
        val needsQuoting = text.isEmpty() ||
            text.first().isWhitespace() ||
            text.last().isWhitespace() ||
            text.first() in "-?:,[]{}#&*!|>'\"%@`" ||
            text.contains(": ") ||
            text.contains(" #")
        return if (needsQuoting) "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else text
    }
}
