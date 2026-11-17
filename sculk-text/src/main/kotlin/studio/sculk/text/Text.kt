package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.sculk.annotation.SculkExperimental
import studio.sculk.annotation.SculkStable
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place Sculk turns a template into a component.
 *
 * Templates are trusted: they come from code, configuration or translation files, and may contain
 * any MiniMessage. Values are not: a player name, a database row or another plugin's output enters
 * only as an unparsed placeholder, or as a component that is already built, so `<red>` in a value is
 * printed, not obeyed. Never substitute a value into a template before calling [render].
 */
@SculkStable
public class Text(
    /** The styles templates may use by name. */
    public val theme: Theme = Theme.DEFAULT,
    internal val miniMessage: MiniMessage = MiniMessage.miniMessage(),
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
) {
    // Keyed by the template alone, or by the template and the styles a render's placeholders hide.
    private val expanded = BoundedCache<Any, String>(cacheSize)

    /** [template] with the theme's styles expanded into MiniMessage tags. */
    internal fun expand(template: String): String = expanded.getOrPut(template) { theme.expand(template) }

    /** [template] expanded for a render through [resolver]; a style whose name [resolver] also has is left to it. */
    private fun expand(template: String, resolver: TagResolver): String {
        if (theme.names.none(resolver::has)) return expand(template)
        val hidden = theme.names.filterTo(HashSet(), resolver::has)
        return expanded.getOrPut(template to hidden) { theme.expand(template, hidden) }
    }

    /**
     * Renders [template] with [values] as placeholders: `render("<primary>Hi <name>", "name" to player.name)`.
     * A [ComponentLike] value is inserted as built; null renders as nothing; anything else is its
     * string form, unparsed. A value named like a theme style takes that name for this render, so
     * `render("<value>", "value" to x)` shows x and the style is not applied. A name that is not
     * lower-case letters, digits, `_` or `-` throws [IllegalArgumentException].
     */
    public fun render(template: String, vararg values: Pair<String, Any?>): Component = render(template, resolverOf(values.asList()))

    /** Renders [template] with the entries of [values] as placeholders, exactly as the vararg overload does. */
    public fun render(template: String, values: Map<String, Any?>): Component = render(template, resolverOf(values.toList()))

    /**
     * Renders [template] with a resolver built elsewhere, such as a placeholder context. A tag
     * [resolver] has takes precedence over a theme style of the same name for this render.
     */
    public fun render(template: String, resolver: TagResolver): Component =
        miniMessage.deserialize(expand(template, resolver), if (OBJECTS) resolver else TagResolver.resolver(resolver, NO_OBJECTS))

    /** This renderer with a different theme and an empty cache. */
    public fun withTheme(theme: Theme): Text = Text(theme, miniMessage, cacheSize)

    private fun resolverOf(values: List<Pair<String, Any?>>): TagResolver {
        if (values.isEmpty()) return TagResolver.empty()
        return TagResolver.resolver(
            values.map { (name, value) ->
                TagNames.requireValid(name, "Placeholder")
                when (value) {
                    is ComponentLike -> Placeholder.component(name, value)
                    null -> Placeholder.component(name, Component.empty())
                    else -> Placeholder.unparsed(name, value.toString())
                }
            },
        )
    }

    @SculkStable
    public companion object {
        /** Expanded templates a renderer keeps unless told otherwise. */
        public const val DEFAULT_CACHE_SIZE: Int = 512

        /** A renderer with [Theme.DEFAULT], shared by [mm] and [send]. Thread-safe. */
        @SculkExperimental
        public val DEFAULT: Text by lazy { Text() }

        /**
         * Whether the server's Adventure can draw `<sprite:atlas:sprite>` icons and `<head:player>` faces
         * inside text: Paper 1.21.11 and every 26.x release can, 1.21.4 cannot. Where it cannot, those
         * tags render as nothing, so one template works on every version.
         */
        @SculkExperimental
        public val supportsObjects: Boolean get() = OBJECTS

        private val OBJECTS: Boolean = try {
            Class.forName("net.kyori.adventure.text.ObjectComponent", false, Component::class.java.classLoader)
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        // Adventure before 4.25 has no object components and would print these tags as text.
        private val NO_OBJECTS = TagResolver.resolver(setOf("sprite", "head")) { _, _ -> Tag.selfClosingInserting(Component.empty()) }
    }
}

/**
 * A cache bounded to [capacity] entries that never locks on a hit. A full cache drops an arbitrary
 * entry to make room, which suits theme expansion: a miss costs little. Thread-safe.
 */
internal class BoundedCache<K : Any, V : Any>(private val capacity: Int) {
    private val entries = ConcurrentHashMap<K, V>()

    val size: Int get() = entries.size

    fun getOrPut(key: K, compute: () -> V): V {
        entries[key]?.let { return it }
        val value = compute()
        if (capacity <= 0) return value
        // Racing writers may each make room and then insert, so the size can pass capacity by at most their number.
        while (entries.size >= capacity) {
            val dropped = entries.keys.firstOrNull() ?: break
            entries.remove(dropped)
        }
        return entries.putIfAbsent(key, value) ?: value
    }
}
