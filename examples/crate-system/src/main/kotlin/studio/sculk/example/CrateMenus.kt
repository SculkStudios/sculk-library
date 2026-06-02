package studio.sculk.example

import org.bukkit.Material
import studio.sculk.gui.Gui
import studio.sculk.gui.gui

public class CrateMenus(private val service: CrateService) {
    public fun preview(crateId: String): Gui = gui("<dark_gray>Crate Preview") {
        size = 54
        border(Material.GRAY_STAINED_GLASS_PANE) {
            name = " "
        }
        val crate = service.crate(crateId)
        if (crate is studio.sculk.SculkResult.Success) {
            val chanceLore = service.chanceLore(crate.value)
            crate.value.rewards.take(45).forEachIndexed { index, reward ->
                item(index) {
                    stack {
                        material(reward.item.material)
                        reward.item.name?.let { name(it) }
                        val lore = reward.item.lore + chanceLore[index]
                        if (lore.isNotEmpty()) lore(lore)
                        amount(reward.item.amount)
                        reward.item.enchantments.forEach { (key, level) -> enchant(key, level) }
                        if (reward.item.glint) glint()
                        reward.item.customModelData?.let { customModelData(it) }
                    }
                    onClick { reply("<yellow>This menu is a preview. Use <white>/crate open $crateId</white>.") }
                }
            }
        }
    }
}
