package studio.sculk.example

import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.annotation.Min
import studio.sculk.items.ItemDescriptor

@ConfigFile("crates.yml")
public data class CrateSettings(
    val crates: Map<String, CrateDefinition> =
        mapOf(
            "vote" to
                CrateDefinition(
                    displayName = "<aqua>Vote Crate",
                    keyItem =
                    ItemDescriptor(
                        material = "tripwire_hook",
                        name = "<aqua>Vote Crate Key",
                        lore = listOf("<gray>Right-click a vote crate to open."),
                        glint = true,
                    ),
                    rewards =
                    listOf(
                        CrateReward(
                            id = "diamonds",
                            weight = 60,
                            item =
                            ItemDescriptor(
                                material = "diamond",
                                name = "<aqua>Diamond Reward",
                                lore = listOf("<gray>A common vote reward."),
                                amount = 3,
                            ),
                        ),
                        CrateReward(
                            id = "rare_sword",
                            weight = 10,
                            broadcast = true,
                            item =
                            ItemDescriptor(
                                material = "diamond_sword",
                                name = "<light_purple>Rare Crate Sword",
                                lore = listOf("<gray>A rare vote crate reward."),
                                enchantments = mapOf("sharpness" to 3),
                                glint = true,
                            ),
                        ),
                    ),
                ),
        ),
)

public data class CrateDefinition(val displayName: String, val keyItem: ItemDescriptor, val rewards: List<CrateReward>)

public data class CrateReward(
    val id: String,
    @param:Min(1)
    val weight: Int,
    val item: ItemDescriptor,
    val broadcast: Boolean = false,
)
