package studio.sculk.example

import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.annotation.Min
import studio.sculk.items.ItemDescriptor

@ConfigFile("kits.yml")
public data class KitSettings(
    val kits: Map<String, KitDefinition> =
        mapOf(
            "starter" to
                KitDefinition(
                    displayName = "<green>Starter Kit",
                    permission = "kits.use.starter",
                    cooldownSeconds = 86_400,
                    icon =
                    ItemDescriptor(
                        material = "chest",
                        name = "<green>Starter Kit",
                        lore = listOf("<gray>A balanced first-day kit."),
                    ),
                    items =
                    listOf(
                        ItemDescriptor(
                            material = "stone_sword",
                            name = "<green>Starter Sword",
                            lore = listOf("<gray>Marked with kit metadata."),
                            data = mapOf("kit_id" to "starter"),
                        ),
                        ItemDescriptor(material = "bread", amount = 16),
                    ),
                ),
        ),
)

public data class KitDefinition(
    val displayName: String,
    val permission: String? = null,
    @param:Min(0)
    val cooldownSeconds: Long = 86_400,
    val icon: ItemDescriptor,
    val items: List<ItemDescriptor>,
)
