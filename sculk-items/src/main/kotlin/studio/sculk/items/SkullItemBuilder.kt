@file:JvmName("SculkItems")
@file:JvmMultifileClass

package studio.sculk.items

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import studio.sculk.annotation.SculkStable
import java.util.UUID
import java.util.function.Consumer

/** Builder for player-head item stacks. */
@SculkStable
public class SkullItemBuilder public constructor() : ItemBuilder(Material.PLAYER_HEAD) {
    private var owner: OfflinePlayer? = null

    /** Sets the skull owner from an offline player. */
    public fun owner(player: OfflinePlayer) {
        owner = player
    }

    /** Sets the skull owner from a UUID. */
    public fun owner(uuid: UUID) {
        owner = Bukkit.getOfflinePlayer(uuid)
    }

    /** Sets the skull owner from a player name. */
    public fun owner(name: String) {
        owner = Bukkit.getOfflinePlayer(name)
    }

    override fun build(): ItemStack {
        val stack = super.build()
        val meta = stack.itemMeta
        if (meta is SkullMeta && owner != null) {
            meta.owningPlayer = owner
            stack.itemMeta = meta
        }
        return stack
    }
}

/** Builds a player-head [ItemStack]. */
@JvmOverloads
@SculkStable
public fun skull(block: SkullItemBuilder.() -> Unit = {}): ItemStack = SkullItemBuilder().apply(block).build()

/** Java-friendly overload of [skull] taking a [Consumer]. */
@SculkStable
public fun skull(block: Consumer<SkullItemBuilder>): ItemStack = SkullItemBuilder().also { block.accept(it) }.build()
