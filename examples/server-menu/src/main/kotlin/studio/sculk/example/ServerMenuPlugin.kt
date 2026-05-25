package studio.sculk.example

import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.command.command
import studio.sculk.core.gui.gui
import studio.sculk.platform.SculkPlatform

public class ServerMenuPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    private val serverMenu =
        gui("<dark_gray>Server Menu") {
            size = 27

            item(11) {
                material = Material.ENDER_PEARL
                name = "<aqua>Warps"
                lore("<gray>Open the warp selector.")
                onClick { reply("<yellow>Warp menu would open here.") }
            }

            item(13) {
                material = Material.PLAYER_HEAD
                name = "<green>Profile"
                lore("<gray>View your profile.")
                onClick { reply("<green>Profile menu would open here.") }
            }

            item(15) {
                material = Material.EMERALD
                name = "<gold>Shop"
                lore("<gray>Buy and sell items.")
                onClick { reply("<gold>Shop menu would open here.") }
            }
        }

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                gui()
            }

        sculk.commands.register(
            command("menu") {
                player {
                    serverMenu.openFor(player!!)
                }
            },
        )
    }

    override fun onDisable() {
        sculk.close()
    }
}
