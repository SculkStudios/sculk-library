package studio.sculk.example

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.core.command.command
import studio.sculk.core.gui.gui
import studio.sculk.items.ItemDescriptor
import studio.sculk.platform.SculkPlatform

public class CrateSystemPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private lateinit var config: CrateConfig

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                config()
                gui()
            }
        config = sculk.config!!.load()

        sculk.commands.register(
            command("crate") {
                sub("preview") {
                    player {
                        cratePreview().openFor(player!!)
                    }
                }
            },
        )
    }

    override fun onDisable() {
        sculk.close()
    }

    private fun cratePreview() =
        gui("<dark_gray>Vote Crate") {
            size = 27
            config.rewards.take(27).forEachIndexed { index, reward ->
                item(index) {
                    stack {
                        material(reward.material)
                        reward.name?.let { name(it) }
                        if (reward.lore.isNotEmpty()) lore(reward.lore)
                        amount(reward.amount)
                        reward.enchantments.forEach { (key, level) -> enchant(key, level) }
                        if (reward.glint) glint()
                        reward.customModelData?.let { customModelData(it) }
                        if (reward.unbreakable) unbreakable()
                        reward.data.forEach { (key, value) -> pdc(key, value) }
                    }
                    onClick { reply("<yellow>This is a preview item.") }
                }
            }
        }
}

@ConfigFile("crates.yml")
public data class CrateConfig(
    val rewards: List<ItemDescriptor> =
        listOf(
            ItemDescriptor(
                material = "diamond",
                name = "<aqua>Diamond Reward",
                lore = listOf("<gray>A common vote crate reward."),
                amount = 3,
                glint = true,
            ),
            ItemDescriptor(
                material = "golden_apple",
                name = "<gold>Golden Apple",
                lore = listOf("<gray>A useful survival reward."),
                amount = 2,
            ),
        ),
)
