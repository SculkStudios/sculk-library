package studio.sculk.items

import org.bukkit.inventory.ItemStack
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.integrations.CustomItems
import studio.sculk.map
import studio.sculk.onFailure
import studio.sculk.series.SculkSeries
import studio.sculk.text.SculkMessages
import java.util.concurrent.ConcurrentHashMap

/**
 * The one place a material key becomes a stack.
 *
 * Two sources, chosen by the prefix. `nexo:`, `oraxen:` and `itemsadder:` go to [CustomItems], which
 * asks that plugin for the item over reflection; everything else goes to [SculkSeries.material] and
 * behaves exactly as it always has. The order matters and only in one direction: nothing that
 * resolved before can start meaning something else, because `Material.matchMaterial` strips a
 * literal `minecraft:` and then deletes non-word characters, so `nexo:ruby_sword` was already
 * `NEXORUBY_SWORD` and already null.
 *
 * Note that [materialByKey] is *not* on this path. It normalises with `substringAfter(':')`, which
 * strips any namespace at all — through it, `nexo:diamond` would quietly become a vanilla diamond
 * rather than reporting that Nexo is missing.
 */
@OptIn(SculkExperimental::class)
internal fun buildItem(key: String, messages: SculkMessages, block: ItemBuilder.() -> Unit): SculkResult<ItemStack> {
    if (CustomItems.handles(key)) {
        return CustomItems
            .resolve(key)
            .map { base -> ItemBuilder(base.type, messages, base).apply(block).build() }
            .onFailure { message, _ -> warnOnce(key, message) }
    }
    val material = SculkSeries.material(key)
        ?: return SculkResult.failure("No material named '$key'.").onFailure { message, _ -> warnOnce(key, message) }
    return SculkResult.success(ItemBuilder(material, messages).apply(block).build())
}

private val warned = ConcurrentHashMap.newKeySet<String>()

/**
 * Says once, per key, why an item could not be built.
 *
 * The result was always returned, and callers that treat an item as optional — a GUI slot resolving
 * a config descriptor with `getOrNull()` — dropped it. The failure was then invisible: an item is
 * simply absent from a menu, with nothing in the console naming the key or saying whether the
 * material was wrong, the plugin was missing, or the plugin had no such item.
 *
 * Once per key, following [ItemCompat.unsupported], because this is reached from menu renders that
 * run several times a second.
 */
private fun warnOnce(key: String, message: String) {
    if (!warned.add(key)) return
    itemLogger.warning(message)
}
