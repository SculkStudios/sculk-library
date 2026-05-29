package studio.sculk.example

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemRarity
import studio.sculk.core.command.command
import studio.sculk.items.ItemDescriptor
import studio.sculk.items.item
import studio.sculk.items.skull
import studio.sculk.items.toItemStack
import studio.sculk.platform.SculkPlugin

public class ItemsPlugin : SculkPlugin({ gui() }) {
    override fun setup() {
        sculk.commands.register(
            command("items") {
                description = "Sculk item API showcase"

                sub("sword") {
                    player {
                        player!!.inventory.addItem(starterSword())
                        reply("<green>Created a starter sword.")
                    }
                }

                sub("head") {
                    player {
                        player!!.inventory.addItem(
                            skull {
                                owner(player!!.uniqueId)
                                name("<aqua>${player!!.name}")
                                lore("<gray>Built with Sculk skull().")
                            },
                        )
                        reply("<green>Created your player head.")
                    }
                }

                sub("apple") {
                    player {
                        player!!.inventory.addItem(healingApple())
                        reply("<green>Created a component-based healing apple.")
                    }
                }

                sub("config") {
                    player {
                        val descriptor =
                            ItemDescriptor(
                                material = "diamond",
                                name = "<aqua>Config Diamond",
                                lore = listOf("<gray>Built from ItemDescriptor."),
                                glint = true,
                                data = mapOf("source" to "descriptor"),
                            )
                        descriptor.toItemStack()?.let { stack -> player!!.inventory.addItem(stack) }
                        reply("<green>Created a descriptor item.")
                    }
                }
            },
        )
    }

    private fun starterSword() =
        item(Material.DIAMOND_SWORD) {
            name("<aqua>Starter Sword")
            lore("<gray>A clean starter weapon.", "", "<yellow>Right-click to inspect.")
            enchant(Enchantment.SHARPNESS, 5)
            glint()
            unbreakable()
            customModelData(1001)
            rarity(ItemRarity.EPIC)
            maxStackSize(1)
            pdc("starter_item", true)
        }

    // Demonstrates the modern data-component surface: the generic component() escape hatch
    // reaches any component (here FOOD) that has no dedicated DSL method.
    private fun healingApple() =
        item(Material.GOLDEN_APPLE) {
            name("<gold>Healing Apple")
            itemName("<gray>Apple")
            lore("<gray>Restores health on eat.")
            rarity(ItemRarity.RARE)
            food(nutrition = 8, saturation = 6f, canAlwaysEat = true)
        }
}
