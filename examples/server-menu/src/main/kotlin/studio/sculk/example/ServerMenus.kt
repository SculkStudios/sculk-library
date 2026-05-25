package studio.sculk.example

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import studio.sculk.core.gui.Gui
import studio.sculk.core.gui.confirmMenu
import studio.sculk.core.gui.gui
import studio.sculk.items.ItemDescriptor
import studio.sculk.items.item

public class ServerMenus(
    private val settings: () -> MenuSettings,
) {
    public fun main(): Gui =
        gui(settings().title) {
            size = 27
            border(Material.GRAY_STAINED_GLASS_PANE) {
                name = " "
            }
            descriptorItem(ServerMenuModel.mainSlots.getValue("warps"), settings().buttons.warps) {
                open(warps())
            }
            descriptorItem(ServerMenuModel.mainSlots.getValue("profile"), settings().buttons.profile) {
                open(profile(player))
            }
            descriptorItem(ServerMenuModel.mainSlots.getValue("players"), settings().buttons.players) {
                val session = onlinePlayers().openFor(player)
                session.setEntries(onlinePlayerItems(Bukkit.getOnlinePlayers().toList()))
            }
            descriptorItem(ServerMenuModel.mainSlots.getValue("settings"), settings().buttons.settings) {
                open(confirmNotifications())
            }
        }

    public fun warps(): Gui =
        gui("<dark_gray>Warps") {
            size = 27
            border(Material.GRAY_STAINED_GLASS_PANE) {
                name = " "
            }
            settings().warps.take(9).forEachIndexed { index, warp ->
                item(9 + index) {
                    material = Material.ENDER_PEARL
                    name = warp.name
                    lore("<gray>${warp.world}: ${warp.x.toInt()}, ${warp.y.toInt()}, ${warp.z.toInt()}")
                    onClick {
                        val world = Bukkit.getWorld(warp.world)
                        if (world == null) {
                            reply("<red>World '${warp.world}' is not loaded.")
                            return@onClick
                        }
                        player.teleport(Location(world, warp.x, warp.y, warp.z))
                        reply("<green>Teleported to ${warp.name}<green>.")
                        close()
                    }
                }
            }
        }

    public fun profile(player: Player): Gui =
        gui("<dark_gray>${player.name}") {
            size = 27
            border(Material.GRAY_STAINED_GLASS_PANE) {
                name = " "
            }
            item(13) {
                material = Material.PLAYER_HEAD
                name = "<aqua>${player.name}"
                lore(
                    "<gray>World: <white>${player.world.name}",
                    "<gray>Health: <red>${player.health.toInt()}",
                    "<gray>Level: <green>${player.level}",
                )
            }
        }

    public fun onlinePlayers(): Gui =
        gui("<dark_gray>Online Players") {
            size = 54
            pagination {
                slots += (0 until 45)
            }
            item(45) {
                material = Material.ARROW
                name = "<gray>Previous"
                onClick { previousPage() }
            }
            item(53) {
                material = Material.ARROW
                name = "<gray>Next"
                onClick { nextPage() }
            }
        }

    public fun confirmNotifications(): Gui =
        confirmMenu("<red>Toggle Menu Tips") {
            confirm {
                name = "<green>Enable Tips"
                lore("<gray>Turns local menu tips on for this session.")
                onClick {
                    reply("<green>Menu tips enabled for this session.")
                    close()
                }
            }
            cancel {
                name = "<red>Keep Disabled"
                lore("<gray>Leave menu tips disabled.")
                onClick {
                    reply("<yellow>No changes made.")
                    close()
                }
            }
        }

    public fun onlinePlayerItems(players: List<Player>): List<ItemStack> =
        if (players.isEmpty()) {
            listOf(
                item(Material.BARRIER) {
                    name("<red>No players online")
                    lore("<gray>Try again later.")
                },
            )
        } else {
            players.map { player ->
                item(Material.PLAYER_HEAD) {
                    name("<aqua>${player.name}")
                    lore("<gray>World: <white>${player.world.name}")
                }
            }
        }

    private fun studio.sculk.core.gui.GuiBuilder.descriptorItem(
        slot: Int,
        descriptor: ItemDescriptor,
        click: studio.sculk.core.gui.GuiContext.() -> Unit,
    ) {
        item(slot) {
            stack {
                material(descriptor.material)
                descriptor.name?.let { name(it) }
                if (descriptor.lore.isNotEmpty()) lore(descriptor.lore)
                amount(descriptor.amount)
                if (descriptor.glint) glint()
                descriptor.customModelData?.let { customModelData(it) }
                descriptor.data.forEach { (key, value) -> pdc(key, value) }
            }
            onClick(click)
        }
    }
}
