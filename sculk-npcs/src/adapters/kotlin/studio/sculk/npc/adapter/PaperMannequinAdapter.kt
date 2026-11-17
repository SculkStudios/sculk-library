package studio.sculk.npc.adapter

import com.destroystokyo.paper.profile.ProfileProperty
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.inventory.MainHand
import studio.sculk.npc.MannequinAdapter

/** Paper 1.21.9 and later. Instantiated by name only, after the capability probe found `Mannequin`. */
internal class PaperMannequinAdapter : MannequinAdapter {
    override fun spawn(location: Location, setup: (LivingEntity) -> Unit): LivingEntity {
        val world = requireNotNull(location.world) { "A mannequin needs a location with a world: $location" }
        return world.spawn(location, Mannequin::class.java) { mannequin -> setup(mannequin) }
    }

    override fun dress(entity: LivingEntity, mainHand: MainHand, immovable: Boolean, description: Component?, showDescription: Boolean) {
        val mannequin = entity as Mannequin
        mannequin.mainHand = mainHand
        mannequin.isImmovable = immovable
        // Paper hides the line for null; leaving it unset keeps Minecraft's translated "NPC" label.
        if (!showDescription) {
            mannequin.description = null
        } else if (description != null) {
            mannequin.description = description
        }
    }

    override fun skin(entity: LivingEntity, value: String?, signature: String?) {
        val mannequin = entity as Mannequin
        // Properties without a name or id build a static profile: clients draw it as given and resolve nothing.
        mannequin.profile = if (value == null) {
            Mannequin.defaultProfile()
        } else {
            ResolvableProfile.resolvableProfile().addProperty(ProfileProperty(TEXTURES, value, signature)).build()
        }
    }

    private companion object {
        const val TEXTURES = "textures"
    }
}
