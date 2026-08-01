package studio.sculk.items

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import studio.sculk.annotation.SculkStable

/**
 * What actually reached the player.
 *
 * [given] is what went into the inventory and [dropped] is what hit the floor, so the two are
 * disjoint and a partly-accepted stack appears in both at its own amount. Anything logging or
 * charging for a delivery wants [given] alone.
 */
@SculkStable
public data class ItemDeliveryResult(public val given: List<ItemStack>, public val dropped: List<ItemStack>) {
    /** True when every item fit. */
    public val fullyDelivered: Boolean get() = dropped.isEmpty()
}

/**
 * Adds [items] to the player's inventory and drops leftovers at their location.
 *
 * This must be called from the server thread or the player's region thread on Folia-like servers.
 */
@SculkStable
public fun Player.giveOrDrop(items: Iterable<ItemStack>): ItemDeliveryResult {
    val given = mutableListOf<ItemStack>()
    val dropped = mutableListOf<ItemStack>()
    for (item in items) {
        val requested = item.amount
        val leftovers = inventory.addItem(item.clone())
        var refused = 0
        for (leftover in leftovers.values) {
            refused += leftover.amount
            dropped += leftover
            world.dropItemNaturally(location, leftover)
        }
        // Reporting the input as `given` counted a stack the inventory refused as delivered, so a
        // full inventory produced the same item in both lists.
        val accepted = requested - refused
        if (accepted > 0) given += item.clone().apply { amount = accepted }
    }
    return ItemDeliveryResult(given = given, dropped = dropped)
}

/**
 * Adds [items] to the player's inventory and drops leftovers at their location.
 */
@SculkStable
public fun Player.giveOrDrop(vararg items: ItemStack): ItemDeliveryResult = giveOrDrop(items.asIterable())
