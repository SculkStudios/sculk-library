package studio.sculk.example

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.inventory.ItemStack
import studio.sculk.adventure.playSound
import studio.sculk.command.command
import studio.sculk.gui.Gui
import studio.sculk.gui.gui
import studio.sculk.platform.SculkPlugin

public class GuiPlugin : SculkPlugin({ gui() }) {
    override fun setup() {
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
            command("deposit") {
                description = "Open an interactive deposit box."
                player { depositBox.openFor(player!!) }
            },
        )
    }
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
                val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: player.health
                name = "<aqua>Profile: <white>${player.name}"
                lore(
                    "<gray>Health: <red>${player.health.toInt()} / ${maxHealth.toInt()}",
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
            onLeftClick {
                close()
                player.playSound(Sound.BLOCK_CHEST_CLOSE)
            }
            onRightClick { actionbar("<gray>Right-click does nothing here.") }
        }

        // Animated banner — cycles wool colours every half-second while the menu is open.
        item(22) {
            name = "<rainbow>Sculk Studio"
            animate(intervalTicks = 10) {
                frame(Material.RED_WOOL)
                frame(Material.YELLOW_WOOL)
                frame(Material.GREEN_WOOL)
                frame(Material.BLUE_WOOL)
            }
        }
    }

// ---------------------------------------------------------------------------
// Deposit box — a hopper-shaped GUI with interactive input slots
// ---------------------------------------------------------------------------

private val depositBox: Gui =
    gui("<gold>Deposit Box") {
        type = org.bukkit.event.inventory.InventoryType.HOPPER
        // Lock the two ends as decoration; leave the middle three as interactive input slots.
        item(0) {
            material = Material.GRAY_STAINED_GLASS_PANE
            name = "<gray> "
        }
        item(4) {
            material = Material.GRAY_STAINED_GLASS_PANE
            name = "<gray> "
        }
        for (slot in 1..3) {
            item(slot) {
                interactive()
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

private fun shopItem(material: Material, label: String, price: Int): ItemStack {
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
