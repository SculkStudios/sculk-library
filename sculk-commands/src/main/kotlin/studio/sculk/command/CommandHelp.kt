package studio.sculk.command

import studio.sculk.annotation.SculkStable

/**
 * Builds help output from the same specs Brigadier was built from.
 *
 * Never hand-write a command list: one written by hand goes stale the first time a subcommand is
 * added, and the version a player sees is then a different document from the one the server
 * actually accepts.
 *
 * Permission filtering takes a `(String) -> Boolean` rather than a sender, both so it tests without
 * a server and so an admin tool can preview what a given rank would see.
 */
@SculkStable
public class CommandHelp(private val text: HelpText = HelpText.DEFAULT, private val perPage: Int = 8) {
    /** Every entry the holder of [hasPermission] may run, in declaration order, grouped by root. */
    @SculkStable
    public fun entries(specs: List<CommandSpec>, hasPermission: (String) -> Boolean): List<Entry> =
        specs.flatMap { spec -> visible(spec, parentPath = "", inherited = spec.permission, hasPermission = hasPermission) }

    /** How many pages [entries] fills. Always at least one, so an empty help still renders. */
    @SculkStable
    public fun pageCount(entries: List<Entry>): Int = maxOf(1, (entries.size + perPage - 1) / perPage)

    /**
     * One page of rendered template lines, ready to hand to the renderer.
     *
     * [page] is one-based and clamped, so `/help 0` and `/help 99` both land on a real page rather
     * than on an empty screen the player reads as "there are no commands".
     */
    @SculkStable
    public fun page(entries: List<Entry>, page: Int = 1): List<Line> {
        val pages = pageCount(entries)
        val current = page.coerceIn(1, pages)
        val lines = mutableListOf<Line>()

        lines += Line(text.header, listOf("page" to current.toString(), "pages" to pages.toString()))
        if (entries.isEmpty()) {
            lines += Line(text.empty, emptyList())
            return lines
        }

        val slice = entries.drop((current - 1) * perPage).take(perPage)
        var lastRoot: String? = null
        for (entry in slice) {
            // A blank line between root commands; without it a long list reads as one block and a
            // player cannot tell where /kit ends and /warp begins.
            if (lastRoot != null && entry.root != lastRoot) lines += Line("", emptyList())
            lastRoot = entry.root

            lines += if (entry.description.isBlank()) {
                Line(text.entryNoDescription, listOf("usage" to entry.usage))
            } else {
                Line(text.entry, listOf("usage" to entry.usage, "description" to entry.description))
            }
        }
        if (text.footer.isNotBlank()) lines += Line(text.footer, emptyList())
        return lines
    }

    private fun visible(spec: CommandSpec, parentPath: String, inherited: String?, hasPermission: (String) -> Boolean): List<Entry> {
        val required = spec.permission ?: inherited
        // A node the sender cannot run hides its children too: Brigadier will not offer them
        // either, and help that lists what the server refuses is worse than no help.
        if (required != null && !hasPermission(required)) return emptyList()

        val path = if (parentPath.isEmpty()) spec.name else "$parentPath ${spec.name}"
        val root = path.substringBefore(' ')
        val here = if (spec.executable) {
            listOf(Entry(root = root, usage = spec.usage(parentPath), description = spec.description))
        } else {
            emptyList()
        }
        return here + spec.children.flatMap { visible(it, path, required, hasPermission) }
    }

    /** One help row. */
    @SculkStable
    public data class Entry(public val root: String, public val usage: String, public val description: String)

    /** A template and its placeholder values, rendered by the caller's message renderer. */
    @SculkStable
    public data class Line(public val template: String, public val values: List<Pair<String, String>>)
}
