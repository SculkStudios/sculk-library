package studio.sculk.example

import org.bukkit.Material
import studio.sculk.core.SculkResult
import studio.sculk.core.gui.Gui
import studio.sculk.core.gui.gui

public class KitMenus(
    private val settings: () -> KitSettings,
    private val service: KitService,
    private val claim: (String, studio.sculk.core.gui.GuiContext) -> Unit,
) {
    public fun list(): Gui =
        gui("<dark_gray>Kits") {
            size = 54
            border(Material.GRAY_STAINED_GLASS_PANE) {
                name = " "
            }
            settings().kits.entries.take(28).forEachIndexed { index, (id, kit) ->
                val slot = 10 + index + (index / 7) * 2
                item(slot) {
                    stack {
                        material(kit.icon.material)
                        kit.icon.name?.let { name(it) }
                        lore(kit.icon.lore + "<yellow>Click to claim.")
                        if (kit.icon.glint) glint()
                        pdc(KitService.KIT_ID_KEY, id)
                    }
                    onClick { claim(id, this) }
                }
            }
        }

    public fun preview(kitId: String): Gui =
        gui("<dark_gray>Kit Preview") {
            size = 54
            border(Material.GRAY_STAINED_GLASS_PANE) {
                name = " "
            }
            val items = service.kitItems(kitId)
            if (items is SculkResult.Success) {
                items.value.take(45).forEachIndexed { index, descriptor ->
                    item(index) {
                        stack {
                            material(descriptor.material)
                            descriptor.name?.let { name(it) }
                            if (descriptor.lore.isNotEmpty()) lore(descriptor.lore)
                            amount(descriptor.amount)
                            descriptor.enchantments.forEach { (key, level) -> enchant(key, level) }
                            if (descriptor.glint) glint()
                            descriptor.data.forEach { (key, value) -> pdc(key, value) }
                        }
                        onClick { claim(kitId, this) }
                    }
                }
            }
        }
}
