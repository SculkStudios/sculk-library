package studio.sculk.example

import org.bukkit.Material
import studio.sculk.core.gui.Gui
import studio.sculk.core.gui.gui

public object ProfileMenus {
    public fun profile(profile: PlayerProfile): Gui =
        gui("<dark_gray>Profile: ${profile.name}") {
            size = 27
            border(Material.GRAY_STAINED_GLASS_PANE) {
                name = " "
            }
            item(11) {
                material = Material.PLAYER_HEAD
                name = "<aqua>${profile.name}"
                lore(
                    "<gray>Joins: <white>${profile.joins}",
                    "<gray>Last seen: <white>${profile.lastSeen}",
                )
            }
            item(13) {
                material = Material.DIAMOND_SWORD
                name = "<red>Combat"
                lore(
                    "<gray>Kills: <white>${profile.kills}",
                    "<gray>Deaths: <white>${profile.deaths}",
                )
            }
            item(15) {
                material = Material.GOLD_INGOT
                name = "<gold>Coins"
                lore("<gray>Balance: <yellow>${profile.coins}")
            }
        }
}
