package gg.sculk.core.gui

import gg.sculk.core.annotation.SculkInternal
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal registry mapping open inventories to their [GuiSession].
 *
 * Used by the platform layer to route inventory click events to the
 * correct session and clean up sessions on close/quit.
 *
 * This is infrastructure. Plugin code never touches this directly.
 */
@SculkInternal
public object GuiRegistry {
    private data class Entry(
        val session: GuiSession,
        val inventory: Inventory,
    )

    private val sessions: ConcurrentHashMap<UUID, Entry> = ConcurrentHashMap()

    /** Set by [SculkPlatform] on bootstrap. Used by [Gui.openFor] to dispatch to the entity scheduler on Folia. */
    @Volatile internal var plugin: JavaPlugin? = null

    /** True when running on Folia or a Folia fork (e.g. Canvas). Set by [SculkPlatform] on bootstrap. */
    @Volatile internal var isFolia: Boolean = false

    /** Called once from [SculkPlatformBuilder.build] to wire up Folia-aware dispatch. */
    @SculkInternal
    public fun init(
        plugin: JavaPlugin,
        isFolia: Boolean,
    ) {
        this.plugin = plugin
        this.isFolia = isFolia
    }

    /** Registers a [session] + [inventory] pair for [player]. */
    public fun register(
        player: Player,
        session: GuiSession,
        inventory: Inventory,
    ) {
        sessions[player.uniqueId] = Entry(session, inventory)
    }

    /** Returns the [GuiSession] for [player] if one is active, or null. */
    public fun sessionFor(player: Player): GuiSession? = sessions[player.uniqueId]?.session

    /** Returns the [GuiSession] for a given [inventory], or null. */
    public fun sessionForInventory(inventory: Inventory): GuiSession? = sessions.values.firstOrNull { it.inventory === inventory }?.session

    /** Removes the session for [player]. Called on inventory close and player quit. */
    public fun unregister(player: Player) {
        sessions.remove(player.uniqueId)
    }

    /** Closes and removes all active sessions. Called on platform shutdown. */
    public fun closeAll() {
        sessions.values.forEach { it.session.close() }
        sessions.clear()
    }
}
