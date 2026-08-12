package studio.sculk.config

private val KEY_LINE = Regex("^(\\s*)([^\\s#:][^:]*):")

/** A block-sequence entry: `- name: value`, or a bare `- value`. */
private val SEQUENCE_ITEM = Regex("^\\s*-(\\s|$)")

/**
 * Adds what a config file is missing without touching what it already has.
 *
 * ### Why a rewrite may only append
 *
 * The obvious implementation renders the decoded object and writes that over the file. It is also
 * destructive in two ways a server owner will not forgive:
 *
 *  - **Keys the data class does not model disappear.** Decoding ignores unknown keys, so they are
 *    absent from the render. An owner who rolled a plugin back would silently lose the newer
 *    version's settings; anything a sister tool writes into the same file would vanish.
 *  - **Comments the owner wrote disappear.** Only `@Comment` text survives a render, so "do not
 *    raise this, it broke the proxy last time" is gone the next time an update adds a key.
 *
 * Neither failure announces itself. So the file on disk is the base, and this only ever inserts
 * blocks for keys that are not in it — existing lines are copied through byte for byte.
 *
 * The cost, stated plainly: a long-lived file drifts from the declaration order of its class, and
 * a key removed from the class stays in the file until someone deletes it. Both are visible and
 * harmless. Losing an owner's data is neither.
 */
internal object ConfigMerge {
    /**
     * The lines that are structural map keys, with everything inside a block sequence removed.
     *
     * A sequence is opaque to this merger, and has to be. Two reasons, both of which produced a file
     * that would not parse:
     *
     *  - **`- id: x` matches [KEY_LINE].** The dash is not a space, a `#` or a `:`, so a list entry
     *    read as a key named `- id`. Worse, YAML lets a sequence sit at its parent's indent, so the
     *    parent was popped off the stack first and the path came out as `vote-menu.- id` rather than
     *    `vote-menu.sites.- id`. Against a file whose sequence was written *indented* — which older
     *    output was — those paths never matched, so the whole list looked missing and a second copy
     *    was appended at an indent where a block sequence has no owning key.
     *  - **Keys inside entries are not addressable.** `sites.cooldown` is not a path; it is a field
     *    on every entry. Appending one `cooldown:` into the section would attach it to whichever
     *    entry happened to come last.
     *
     * So a sequence is either present or absent as a whole. A field added to an entry type does not
     * get backfilled into entries already on disk — decoding supplies the data class default for it,
     * which is the same value the render would have written.
     */
    private fun structuralLines(lines: List<String>): List<IndexedValue<String>> {
        val kept = mutableListOf<IndexedValue<String>>()
        var sequenceIndent: Int? = null

        for ((index, line) in lines.withIndex()) {
            if (line.isBlank() || line.trimStart().startsWith("#")) {
                if (sequenceIndent == null) kept += IndexedValue(index, line)
                continue
            }
            val indent = line.length - line.trimStart().length
            val floor = sequenceIndent
            if (floor != null) {
                // Deeper than the entries: their contents.
                if (indent > floor) continue
                // At the entries' own indent: another entry continues the sequence, but a key ends
                // it. YAML allows a sequence to sit level with its sibling keys, so `buttons:` and
                // `- id:` can share an indent and mean entirely different things.
                if (indent == floor && SEQUENCE_ITEM.containsMatchIn(line)) continue
                sequenceIndent = null
            }
            if (SEQUENCE_ITEM.containsMatchIn(line)) {
                sequenceIndent = indent
                continue
            }
            kept += IndexedValue(index, line)
        }
        return kept
    }

    /**
     * Returns [original] plus any key in [rendered] it does not already contain.
     *
     * Both are expected to use `\n` and the same two-space indentation, which they do: one is the
     * previous output of this system and the other is what kaml just produced.
     */
    fun appendMissing(original: String, rendered: String): String {
        val existing = pathsIn(original)
        val blocks = blocksIn(rendered).filter { it.path !in existing }
        if (blocks.isEmpty()) return original

        val lines = original.trimEnd('\n').lines().toMutableList()
        // Deepest first, so inserting into a nested section cannot shift the index of a section
        // that has not been handled yet.
        for (block in blocks.sortedByDescending { it.depth }) {
            insert(lines, block)
        }
        return lines.joinToString("\n").trimEnd('\n') + "\n"
    }

    private fun insert(lines: MutableList<String>, block: Block) {
        val parent = block.path.substringBeforeLast('.', "")
        val at = if (parent.isEmpty()) lines.size else endOfSection(lines, parent)
        if (at == -1) {
            // The parent section is missing too; it will arrive as its own block.
            return
        }
        val payload = buildList {
            if (lines.isNotEmpty() && lines.getOrNull(at - 1)?.isNotBlank() == true) add("")
            addAll(block.lines)
        }
        lines.addAll(at, payload)
    }

    /**
     * The index just past the last line belonging to [path], or -1 when it is not present.
     *
     * "Just past" stops short of the comment lines introducing whatever comes next, for the same
     * reason [endOfBlock] exists: inserting at the next key's own index puts the new block between
     * that key and its documentation, leaving the comment stranded above a setting it does not
     * describe and the key below it bare.
     */
    private fun endOfSection(lines: List<String>, path: String): Int {
        var indent = -1
        var start = -1
        val stack = ArrayDeque<Pair<Int, String>>()

        for ((index, line) in structuralLines(lines)) {
            val match = KEY_LINE.find(line) ?: continue
            val lineIndent = match.groupValues[1].length
            while (stack.isNotEmpty() && stack.last().first >= lineIndent) stack.removeLast()
            stack.addLast(lineIndent to match.groupValues[2].trim())

            if (stack.joinToString(".") { it.second } == path) {
                indent = lineIndent
                start = index
                continue
            }
            if (indent >= 0 && lineIndent <= indent) return endOfBlock(lines, start, index)
        }
        return if (indent >= 0) endOfBlock(lines, start, lines.size) else -1
    }

    /** Every top-level and nested key path present in [text]. */
    private fun pathsIn(text: String): Set<String> {
        val paths = mutableSetOf<String>()
        val stack = ArrayDeque<Pair<Int, String>>()
        for ((_, line) in structuralLines(text.lines())) {
            val match = KEY_LINE.find(line) ?: continue
            val indent = match.groupValues[1].length
            val key = match.groupValues[2].trim()
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
            stack.addLast(indent to key)
            paths += stack.joinToString(".") { it.second }
        }
        return paths
    }

    /** Each key in [text] with the comment lines above it and everything nested beneath it. */
    private fun blocksIn(text: String): List<Block> {
        val lines = text.lines()
        val blocks = mutableListOf<Block>()
        val stack = ArrayDeque<Pair<Int, String>>()
        var pendingComments = mutableListOf<String>()

        for ((index, line) in structuralLines(lines)) {
            if (line.isBlank()) {
                pendingComments = mutableListOf()
                continue
            }
            if (line.trimStart().startsWith("#")) {
                pendingComments += line
                continue
            }
            val match = KEY_LINE.find(line)
            if (match == null) {
                pendingComments = mutableListOf()
                continue
            }

            val indent = match.groupValues[1].length
            val key = match.groupValues[2].trim()
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
            stack.addLast(indent to key)

            val end = extentOf(lines, index, indent)
            blocks += Block(
                path = stack.joinToString(".") { it.second },
                depth = stack.size,
                lines = pendingComments + lines.subList(index, end),
            )
            pendingComments = mutableListOf()
        }
        return blocks
    }

    /** Where the block starting at [start] ends: the first later line at or above its indent. */
    private fun extentOf(lines: List<String>, start: Int, indent: Int): Int {
        var sequenceIndent: Int? = null
        for (index in start + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val lineIndent = line.length - line.trimStart().length

            // A sequence belongs to the key above it and must be carried with it, even though YAML
            // lets its entries sit at that key's own indent. Terminating here instead would emit a
            // `sites:` block with none of its sites, and then append that empty key to a file that
            // already had the list.
            val floor = sequenceIndent
            if (floor != null) {
                if (lineIndent > floor) continue
                if (lineIndent == floor && SEQUENCE_ITEM.containsMatchIn(line)) continue
                sequenceIndent = null
            }
            if (SEQUENCE_ITEM.containsMatchIn(line)) {
                sequenceIndent = lineIndent
                continue
            }

            val match = KEY_LINE.find(line) ?: continue
            if (match.groupValues[1].length <= indent) return endOfBlock(lines, start, index)
        }
        return endOfBlock(lines, start, lines.size)
    }

    /**
     * Backs [end] up over the blank and comment lines immediately before it.
     *
     * A comment sits *above* the key it documents, so the run of comments and blanks just before the
     * next key belongs to that key — not to the block being closed here. Including them meant an
     * inserted block arrived carrying a copy of the following key's comment, while the original
     * stayed where it was:
     *
     *     # The port to listen on.          <- original
     *
     *       # How many votes a minute.
     *       rate-limit: 20
     *     # The port to listen on.          <- the copy, carried in with the block
     *     port: 8192
     *
     * One more copy landed with every release that added a nested key, which is what "the comments
     * are doubled up" looks like on a file that has been upgraded a few times.
     */
    private fun endOfBlock(lines: List<String>, start: Int, end: Int): Int {
        var last = end
        while (last > start + 1) {
            val previous = lines[last - 1]
            if (previous.isBlank() || previous.trimStart().startsWith("#")) last-- else break
        }
        return last
    }

    private class Block(val path: String, val depth: Int, val lines: List<String>)
}
