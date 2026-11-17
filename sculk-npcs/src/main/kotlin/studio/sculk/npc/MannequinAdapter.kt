package studio.sculk.npc

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.MainHand
import studio.sculk.annotation.SculkInternal

/** Spawns and dresses Paper's `Mannequin`, which exists from Minecraft 1.21.9 (ADR-0028, ADR-0033). Not API. */
@SculkInternal
public interface MannequinAdapter {
    /**
     * Spawns a mannequin at [location] with [setup] applied before it enters the world, so the first
     * packet a client gets already shows it set up. The caller owns the region of [location].
     */
    public fun spawn(location: Location, setup: (LivingEntity) -> Unit): LivingEntity

    /**
     * Sets what only a mannequin has. [description] null keeps Minecraft's label unless
     * [showDescription] is false, which hides the line. The caller owns [entity].
     */
    public fun dress(entity: LivingEntity, mainHand: MainHand, immovable: Boolean, description: Component?, showDescription: Boolean)

    /** Shows the `textures` property [value] signed by [signature], or the default skin for null. The caller owns [entity]. */
    public fun skin(entity: LivingEntity, value: String?, signature: String?)
}

internal object MannequinAdapters {
    private const val ADAPTER = "studio.sculk.npc.adapter.PaperMannequinAdapter"

    // Called only once PaperCapabilities.MANNEQUINS reported the class, so the adapter, compiled
    // against the newest anchor, is never linked on a server without mannequins.
    fun load(): MannequinAdapter =
        Class.forName(ADAPTER, true, MannequinAdapters::class.java.classLoader).getDeclaredConstructor().newInstance() as MannequinAdapter
}
