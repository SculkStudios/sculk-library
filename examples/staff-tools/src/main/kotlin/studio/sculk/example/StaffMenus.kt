package studio.sculk.example

import org.bukkit.Material
import org.bukkit.entity.Player
import studio.sculk.gui.Gui
import studio.sculk.gui.gui

public object StaffMenus {
    public fun inspect(target: Player): Gui = gui("<dark_gray>Inspect: ${target.name}") {
        size = 27
        border(Material.GRAY_STAINED_GLASS_PANE) {
            name = " "
        }
        item(10) {
            material = Material.PLAYER_HEAD
            name = "<aqua>${target.name}"
            lore(
                "<gray>UUID: <white>${target.uniqueId}",
                "<gray>World: <white>${target.world.name}",
            )
        }
        item(12) {
            material = Material.COOKED_BEEF
            name = "<gold>Vitals"
            lore(
                "<gray>Health: <red>${target.health.toInt()}",
                "<gray>Food: <yellow>${target.foodLevel}",
                "<gray>Level: <green>${target.level}",
            )
        }
        item(14) {
            material = Material.ENDER_CHEST
            name = "<light_purple>Inventory"
            lore("<gray>Use this slot as the entry point for future inventory inspection.")
        }
        item(16) {
            material = Material.ICE
            name = "<aqua>Freeze Status"
            lore("<gray>Use <white>/freeze ${target.name}</white> to toggle freeze state.")
        }
    }
}
