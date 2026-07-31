package studio.sculk.hud

import org.bukkit.entity.Player
import studio.sculk.annotation.SculkStable

/**
 * Per-viewer values a HUD template can reference.
 *
 * ### Why this is an array and not a map
 *
 * [resolve] runs for every row of every sidebar for every player, four times a second. Iterating a
 * `ConcurrentHashMap` with `forEach { (name, value) -> }` goes through the entry-set iterator and
 * materialises a `Map.Entry` at each step, which is pure allocation on the hottest path in the
 * module. Registration happens at start-up and essentially never again, so paying for a copy there
 * to make every read an indexed walk over an array is the right way round.
 *
 * The `<name>` token is pre-built for the same reason: `"<$name>"` inside the loop builds a string
 * per placeholder per row per player per refresh.
 */
@SculkStable
public class Placeholders {
    @Volatile private var snapshot: Array<Entry> = emptyArray()

    private val registered = LinkedHashMap<String, (Player) -> String>()

    /** Registers [resolver] under [name], to be referenced in a template as `<name>`. */
    @SculkStable
    public fun register(name: String, resolver: (Player) -> String) {
        synchronized(registered) {
            registered[name] = resolver
            rebuild()
        }
    }

    @SculkStable
    public fun unregister(name: String) {
        synchronized(registered) {
            registered.remove(name)
            rebuild()
        }
    }

    @SculkStable
    public val names: Set<String> get() = snapshot.map { it.name }.toSet()

    /**
     * The values [template] actually mentions, ready to hand to the renderer.
     *
     * Only what the template mentions: resolving every registered placeholder for every row would
     * make a plugin's twentieth placeholder cost something on rows that never use it.
     *
     * A resolver that throws yields `"?"` rather than taking the frame down. A broken placeholder
     * should be a visibly wrong value in one slot, not a sidebar that stops updating.
     */
    @SculkStable
    public fun resolve(player: Player, template: String): Array<Pair<String, String>> {
        val current = snapshot
        if (current.isEmpty()) return EMPTY

        var matched: MutableList<Pair<String, String>>? = null
        for (entry in current) {
            if (!template.contains(entry.token)) continue
            val value = try {
                entry.resolver(player)
            } catch (_: Exception) {
                // Exception, not Throwable: an OutOfMemoryError is not this function's to swallow.
                "?"
            }
            (matched ?: mutableListOf<Pair<String, String>>().also { matched = it }) += entry.name to value
        }
        return matched?.toTypedArray() ?: EMPTY
    }

    private fun rebuild() {
        snapshot = registered.map { (name, resolver) -> Entry(name, "<$name>", resolver) }.toTypedArray()
    }

    private class Entry(val name: String, val token: String, val resolver: (Player) -> String)

    private companion object {
        // Shared so the common "this row mentions nothing" case allocates nothing at all.
        val EMPTY = emptyArray<Pair<String, String>>()
    }
}
