package studio.sculk.example.shop

import kotlinx.serialization.Serializable
import org.bukkit.Material
import studio.sculk.config.Comment
import studio.sculk.config.Range
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup

@Serializable
data class ShopSettings(
    @Comment("What the shop sells: an item, how many come at once, and the price in emeralds.")
    val offers: List<Offer> = listOf(
        Offer("bread", 16, 1),
        Offer("oak_log", 32, 2),
        Offer("torch", 64, 2),
        Offer("iron_ingot", 8, 4),
        Offer("golden_apple", 1, 6),
        Offer("diamond", 1, 10),
    ),
    @Comment("The trader's offers, in the same form; vanilla trading moves the items.")
    val trades: List<Offer> = listOf(Offer("cod", 4, 1), Offer("fishing_rod", 1, 3)),
    @Comment("The most of one offer a player can buy at once.")
    @Range(min = 1, max = 64)
    val maxQuantity: Int = 16,
)

@Serializable
data class Offer(val item: String, @Range(min = 1, max = 64) val amount: Int = 1, @Range(min = 1, max = 64) val price: Int = 1) {
    /** The item this offer names, or null when no item has that name. */
    fun material(): Material? = Material.matchMaterial(item)?.takeIf { it.isItem }
}

object ShopPermissions : PermissionGroup("shop") {
    val buy = permission("buy", "Buy from the shop and the trader", PermissionDefault.EVERYONE)
}

/** What [quantity] of [offer] costs, or null when [quantity] is outside 1..[max]. */
fun cost(offer: Offer, quantity: Int, max: Int): Int? = if (quantity in 1..max) offer.price * quantity else null
