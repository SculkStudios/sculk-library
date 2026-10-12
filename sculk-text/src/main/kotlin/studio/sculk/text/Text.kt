package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.sculk.annotation.SculkStable

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
    public val theme: Theme = Theme.DEFAULT,
    internal val miniMessage: MiniMessage = MiniMessage.miniMessage(),
    private val cacheSize: Int = DEFAULT_CACHE_SIZE,
) {
    private val expanded = BoundedCache<String, String>(cacheSize)

    /** [template] with the theme's styles expanded into MiniMessage tags. */
    internal fun expand(template: String): String = expanded.getOrPut(template) { theme.expand(template) }

    /**
     * Renders [template] with [values] as placeholders: `render("<primary>Hi <name>", "name" to player.name)`.
     * A [ComponentLike] value is inserted as built; null renders as nothing; anything else is its
     * string form, unparsed.
     */
    public fun render(template: String, vararg values: Pair<String, Any?>): Component = render(template, resolverOf(values.asList()))

    public fun render(template: String, values: Map<String, Any?>): Component = render(template, resolverOf(values.toList()))

    /** Renders [template] with a resolver built elsewhere, such as a placeholder context. */
    public fun render(template: String, resolver: TagResolver): Component = miniMessage.deserialize(expand(template), resolver)

    /** This renderer with a different theme and an empty cache. */
    public fun withTheme(theme: Theme): Text = Text(theme, miniMessage, cacheSize)

    private fun resolverOf(values: List<Pair<String, Any?>>): TagResolver {
        if (values.isEmpty()) return TagResolver.empty()
        return TagResolver.resolver(
            values.map { (name, value) ->
                require(NAME.matches(name)) { "Placeholder '$name' must be lower-case letters, digits, '_' or '-'." }
                require(theme[name] == null) { "Placeholder '$name' has the same name as a theme style; rename one of them." }
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
        public const val DEFAULT_CACHE_SIZE: Int = 512
        private val NAME = Regex("[a-z0-9_-]+")
    }
}

/** A small access-ordered cache; theme expansion is cheap, so a miss costs little. Thread-safe. */
internal class BoundedCache<K : Any, V : Any>(private val capacity: Int) {
    private val entries = object : LinkedHashMap<K, V>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
    }

    fun getOrPut(key: K, compute: () -> V): V = synchronized(entries) { entries.getOrPut(key, compute) }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
