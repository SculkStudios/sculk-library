package studio.sculk.example

import org.bukkit.Material
import org.bukkit.entity.Player
import studio.sculk.command.command
import studio.sculk.gui.gui
import studio.sculk.platform.SculkPlugin

public class BasicPlugin :
    SculkPlugin({
        gui()
        config()
    }) {
    override fun setup() {
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
