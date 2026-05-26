package studio.sculk.items

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Result of giving items to a player inventory.
 */
public data class ItemDeliveryResult(
    public val given: List<ItemStack>,
    public val dropped: List<ItemStack>,
)

/**
 * Adds [items] to the player's inventory and drops leftovers at their location.
 *
 * This must be called from the server thread or the player's region thread on Folia-like servers.
 */
public fun Player.giveOrDrop(items: Iterable<ItemStack>): ItemDeliveryResult {
    val stacks = items.map(ItemStack::clone)
    val dropped = mutableListOf<ItemStack>()
    for (stack in stacks) {
        val leftovers = inventory.addItem(stack)
        leftovers.values.forEach { leftover ->
            dropped += leftover
            world.dropItemNaturally(location, leftover)
        }
    }
    return ItemDeliveryResult(given = stacks, dropped = dropped)
}

/**
 * Adds [items] to the player's inventory and drops leftovers at their location.
 */
public fun Player.giveOrDrop(vararg items: ItemStack): ItemDeliveryResult = giveOrDrop(items.asIterable())
