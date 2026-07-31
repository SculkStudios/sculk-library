package studio.sculk.text

import studio.sculk.annotation.SculkStable
import java.util.concurrent.ConcurrentHashMap

/**
 * A palette of named styles, so messages are written against meaning rather than colour.
 *
 * A message says `<danger>` and not `<red>`. Changing what danger looks like is then one edit in
 * one place instead of a search for every red string, and a server running a seasonal palette
 * swaps the whole look without touching a single message.
 *
 * Expansion happens *before* MiniMessage parses, so a style can be a gradient — MiniMessage has
 * no way to express "this named thing is a scoped gradient" as a tag.
 *
 * ```kotlin
 * val theme = SculkTheme(
 *     mapOf(
 *         "danger" to ThemeStyle.Solid("#ff5f5f"),
 *         "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
 *     ),
 * )
 * theme.expand("<danger>Not enough <value>coins</value></danger>")
 * ```
 */
@SculkStable
public class SculkTheme(styles: Map<String, ThemeStyle>) {
    private val styles: Map<String, ThemeStyle> = styles.toMap()

    /**
     * Pre-built substitutions, one per style.
     *
     * Built once here rather than per call: [expand] runs on every rendered message, and
     * assembling `"<$name>"` and `"</$name>"` for every style on every message allocated two
     * strings per style per message for values that never change.
     */
    private val tags: List<Tag> = styles.map { (name, style) -> Tag("<$name>", "</$name>", style) }

    private val cache = ConcurrentHashMap<String, String>()

    /** The style names this theme defines. */
    @SculkStable
    public val names: Set<String> get() = styles.keys

    @SculkStable
    public fun get(name: String): ThemeStyle? = styles[name]

    /** Rewrites every `<name>` and `</name>` this theme knows into MiniMessage. */
    @SculkStable
    public fun expand(template: String): String {
        if (tags.isEmpty()) return template
        return cache.computeIfAbsent(template) { source ->
            var expanded = source
            for (tag in tags) {
                if (!expanded.contains(tag.open) && !expanded.contains(tag.close)) continue
                expanded = expanded.replace(tag.open, tag.style.open).replace(tag.close, tag.style.close)
            }
            expanded
        }
    }

    /**
     * A copy with [overrides] layered on top.
     *
     * For reloading a palette from config without rebuilding the renderer and everything holding
     * a reference to it.
     */
    @SculkStable
    public fun with(overrides: Map<String, ThemeStyle>): SculkTheme = SculkTheme(styles + overrides)

    private class Tag(val open: String, val close: String, val style: ThemeStyle)

    public companion object {
        /** An empty palette. Messages render as plain MiniMessage. */
        @SculkStable
        public val EMPTY: SculkTheme = SculkTheme(emptyMap())
    }
}
