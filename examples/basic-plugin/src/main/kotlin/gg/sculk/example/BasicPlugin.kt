package gg.sculk.example

import gg.sculk.core.command.command
import gg.sculk.core.gui.gui
import gg.sculk.platform.SculkPlatform
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

public class BasicPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                gui()
                config()
            }

        sculk.commands.register(
            command("hello") {
                description = "Say hello and open a demo GUI."
                permission = "basic.hello"

                player {
                    reply("<gradient:aqua:blue><bold>Hello, ${player!!.name}!</bold></gradient>")
                    openDemoGui(player!!)
                }
            },
        )
    }

    override fun onDisable(): Unit = sculk.close()
}

private fun openDemoGui(player: Player) {
    gui("<dark_aqua><bold>Sculk Demo") {
        size = 27

        item(13) {
            material = Material.DIAMOND
            name = "<aqua>You found it!"
            lore("<gray>This GUI is powered by Sculk Studio.")
            onClick {
                reply("<green>Clicked the diamond!")
                close()
            }
        }

        // Corner border items
        for (slot in listOf(0, 8, 18, 26)) {
            item(slot) {
                material = Material.BLACK_STAINED_GLASS_PANE
                name = "<gray> "
            }
        }
    }.openFor(player)
}
