package studio.sculk.placeholder

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.sculk.getOrElse

/** Who a render is for: the [audience] audience placeholders describe, and the pair relational ones need. */
internal class Bound(val audience: Audience?, val viewer: Audience?, val target: Audience?)

/**
 * The registry's placeholders as MiniMessage tags for one [bound] render. A tag whose placeholder
 * needs an audience the render does not have is not claimed, so MiniMessage leaves it as written.
 */
internal class BoundResolver(private val registry: PlaceholderRegistry, private val bound: Bound) : TagResolver {
    override fun has(name: String): Boolean = registry.find(name)?.accepts(bound) == true

    override fun resolve(name: String, arguments: ArgumentQueue, ctx: Context): Tag? {
        val placeholder = registry.find(name)?.takeIf { it.accepts(bound) } ?: return null
        val raw = buildList { while (arguments.hasNext()) add(arguments.pop().value()) }
        // Arguments reach the placeholder as strings; the component it returns is inserted as built, never parsed.
        val component = placeholder.component(bound, raw).getOrElse {
            arguments.reset()
            throw ctx.newException(it.describe(), arguments)
        }
        return Tag.selfClosingInserting(component)
    }
}
