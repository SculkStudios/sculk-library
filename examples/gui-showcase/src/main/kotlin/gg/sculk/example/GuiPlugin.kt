package gg.sculk.example

import gg.sculk.core.adventure.playSound
import gg.sculk.core.command.command
import gg.sculk.core.gui.Gui
import gg.sculk.core.gui.gui
import gg.sculk.platform.SculkPlatform
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

public class GuiPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                gui()
            }

        sculk.commands.registerAll(
            command("menu") {
                description = "Open the main menu."
                player {
                    mainMenu.openFor(player!!)
                }
            },
            command("shop") {
                description = "Open the paginated shop."
                player {
                    val session = shopMenu.openFor(player!!)
                    session.setEntries(shopItems())
                }
            },
        )
    }

    override fun onDisable(): Unit = sculk.close()
}

// ---------------------------------------------------------------------------
// Main menu — static GUI with click routing and GUI switching
// ---------------------------------------------------------------------------

private val mainMenu: Gui =
    gui("<dark_aqua><bold>Main Menu") {
        size = 27

        item(11) {
            material = Material.EMERALD
            name = "<green><bold>Shop"
            lore("<gray>Browse available items.")
            glow = true
            onClick {
                val session = shopMenu.openFor(player)
                session.setEntries(shopItems())
            }
        }

        item(13) {
            material = Material.PLAYER_HEAD
            name = "<aqua><bold>Profile"
            dynamicContent { player ->
                name = "<aqua>Profile: <white>${player.name}"
                lore(
                    "<gray>Health: <red>${player.health.toInt()} / ${player.maxHealth.toInt()}",
                    "<gray>Level: <yellow>${player.level}",
                )
            }
            onClick {
                reply("<aqua>Your profile data is shown above.")
            }
        }

        item(15) {
            material = Material.BARRIER
            name = "<red><bold>Close"
            lore("<gray>Close this menu.")
            onClick {
                close()
                player.playSound(Sound.BLOCK_CHEST_CLOSE)
            }
        }
    }

// ---------------------------------------------------------------------------
// Shop — paginated GUI with GuiState-driven category filter
// ---------------------------------------------------------------------------

private val shopMenu: Gui =
    gui("<green><bold>Shop") {
        size = 54

        pagination {
            slots += (0 until 45).toList() // top 5 rows hold entries
        }

        // Category filter indicator — re-rendered with refresh(slot) on category change
        item(46) {
            material = Material.HOPPER
            name = "<yellow>Filter"
            dynamicContent { _ ->
                // GuiState isn't accessible from item definitions — see onClick below
                lore("<gray>Click to cycle categories.")
            }
            onClick {
                // Cycle through categories stored in session state
                val current = session.state["category"] as? String ?: "all"
                val next =
                    when (current) {
                        "all" -> "tools"
                        "tools" -> "blocks"
                        "blocks" -> "all"
                        else -> "all"
                    }
                session.state["category"] = next
                session.setEntries(shopItems(next))
                actionbar("<gray>Filter: <yellow>$next")
            }
        }

        item(45) {
            material = Material.ARROW
            name = "<gray>← Previous"
            onClick { previousPage() }
        }

        item(53) {
            material = Material.ARROW
            name = "<gray>Next →"
            onClick { nextPage() }
        }

        item(49) {
            material = Material.DARK_OAK_DOOR
            name = "<gray>← Back"
            onClick { open(mainMenu) }
        }
    }

// ---------------------------------------------------------------------------
// Sample shop entries — real plugin would load from config or database
// ---------------------------------------------------------------------------

private fun shopItems(category: String = "all"): List<ItemStack> {
    val all =
        listOf(
            shopItem(Material.DIAMOND_SWORD, "Diamond Sword", 250),
            shopItem(Material.DIAMOND_PICKAXE, "Diamond Pickaxe", 300),
            shopItem(Material.BOW, "Bow", 80),
            shopItem(Material.STONE_BRICKS, "Stone Bricks ×64", 10),
            shopItem(Material.OAK_LOG, "Oak Logs ×64", 8),
            shopItem(Material.SAND, "Sand ×64", 5),
            shopItem(Material.IRON_CHESTPLATE, "Iron Chestplate", 120),
            shopItem(Material.GOLDEN_HELMET, "Golden Helmet", 90),
        )
    return when (category) {
        "tools" -> all.filter { it.type.name.contains("SWORD") || it.type.name.contains("PICKAXE") || it.type == Material.BOW }
        "blocks" -> all.filter { it.type == Material.STONE_BRICKS || it.type == Material.OAK_LOG || it.type == Material.SAND }
        else -> all
    }
}

private fun shopItem(
    material: Material,
    label: String,
    price: Int,
): ItemStack {
    val stack = ItemStack(material)
    val meta = stack.itemMeta ?: return stack
    meta.displayName(
        net.kyori.adventure.text.minimessage.MiniMessage
            .miniMessage()
            .deserialize("<white>$label <dark_gray>| <gold>$price coins"),
    )
    stack.itemMeta = meta
    return stack
}
