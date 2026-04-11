package gg.sculk.core.gui

import gg.sculk.core.annotation.SculkStable

/**
 * Mutable state container for a [GuiSession].
 *
 * Plugins can store arbitrary key-value pairs here and reference them
 * in click handlers or refresh callbacks to drive dynamic GUI content.
 *
 * Example:
 * ```kotlin
 * gui("Shop") {
 *     item(13) {
 *         onClick {
 *             state["page"] = (state["page"] as? Int ?: 0) + 1
 *             session.refresh()
 *         }
 *     }
 * }
 * ```
 */
@SculkStable
public class GuiState {
    private val backing: MutableMap<String, Any?> = mutableMapOf()

    /** Retrieves the value for [key], or null if not present. */
    public operator fun get(key: String): Any? = backing[key]

    /** Stores [value] under [key]. */
    public operator fun set(
        key: String,
        value: Any?,
    ) {
        backing[key] = value
    }

    /** Returns true if [key] is present. */
    public operator fun contains(key: String): Boolean = key in backing

    /** Removes all entries. */
    public fun clear(): Unit = backing.clear()
}
