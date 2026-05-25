package studio.sculk.example

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.command.command
import studio.sculk.items.ItemDescriptor
import studio.sculk.items.item
import studio.sculk.items.skull
import studio.sculk.items.toItemStack
import studio.sculk.platform.SculkPlatform

public class ItemsPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                gui()
            }

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

    override fun onDisable() {
        sculk.close()
    }

    private fun starterSword() =
        item(Material.DIAMOND_SWORD) {
            name("<aqua>Starter Sword")
            lore("<gray>A clean starter weapon.", "", "<yellow>Right-click to inspect.")
            enchant(Enchantment.SHARPNESS, 5)
            glint()
            unbreakable()
            customModelData(1001)
            pdc("starter_item", true)
        }
}
