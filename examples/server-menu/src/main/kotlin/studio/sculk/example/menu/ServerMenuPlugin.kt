package studio.sculk.example.menu

import org.bukkit.Material
import studio.sculk.command.command
import studio.sculk.gui.gui
import studio.sculk.platform.SculkPlugin
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle

private data class Warp(val name: String, val icon: Material, val description: String)

/** Menus: borders, per-click handlers, dynamic content and paging. */
class ServerMenuPlugin : SculkPlugin() {
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Gradient(listOf("#bd93f9", "#ff79c6")),
            "dim" to ThemeStyle.Solid("#6272a4"),
        ),
    )

    private val warps = listOf(
        Warp("Spawn", Material.BEACON, "Where everyone starts."),
        Warp("Mines", Material.IRON_PICKAXE, "Ore, mostly."),
        Warp("Nether", Material.NETHERRACK, "Bring boots."),
        Warp("End", Material.END_STONE, "Bring more boots."),
        Warp("Shop", Material.EMERALD, "Spend your coins."),
    )

    override fun setup() {
        +command("warps") {
            description = "Opens the warp menu."
            player {
                val viewer = player!!
                sculk.menus.open(warpMenu(), viewer)
            }
        }
    }

    private fun warpMenu() = gui("<value>Warps</value>") {
        size = 45
        border(Material.PURPLE_STAINED_GLASS_PANE) { name = " " }

        warps.forEachIndexed { index, warp ->
            item(10 + index) {
                material = warp.icon
                name = "<value>${warp.name}</value>"
                lore("<dim>${warp.description}", "", "<dim>Left click to travel.")
                onLeftClick {
                    reply("Travelling to <value><warp></value>…", "warp" to warp.name)
                    close()
                }
                onRightClick {
                    reply("<dim><warp>: <description></dim>", "warp" to warp.name, "description" to warp.description)
                }
            }
        }

        item(40) {
            material = Material.BARRIER
            name = "<value>Close</value>"
            onClick { close() }
        }
    }
}
