package studio.sculk.example

import studio.sculk.config.annotation.ConfigFile
import studio.sculk.items.ItemDescriptor

@ConfigFile("menus.yml")
public data class MenuSettings(
    val title: String = "<dark_gray>Server Menu",
    val buttons: MenuButtons = MenuButtons(),
    val warps: List<WarpDefinition> =
        listOf(
            WarpDefinition("spawn", "<green>Spawn", "world", 0.5, 80.0, 0.5),
            WarpDefinition("wild", "<yellow>Wild", "world", 500.5, 90.0, 500.5),
        ),
)

public data class MenuButtons(
    val warps: ItemDescriptor =
        ItemDescriptor(
            material = "ender_pearl",
            name = "<aqua>Warps",
            lore = listOf("<gray>Choose a server warp."),
        ),
    val profile: ItemDescriptor =
        ItemDescriptor(
            material = "player_head",
            name = "<green>Profile",
            lore = listOf("<gray>View your local profile card."),
        ),
    val players: ItemDescriptor =
        ItemDescriptor(
            material = "compass",
            name = "<gold>Online Players",
            lore = listOf("<gray>Browse who is online."),
        ),
    val settings: ItemDescriptor =
        ItemDescriptor(
            material = "redstone",
            name = "<red>Settings",
            lore = listOf("<gray>Toggle local menu preferences."),
        ),
)

public data class WarpDefinition(val id: String, val name: String, val world: String, val x: Double, val y: Double, val z: Double)
