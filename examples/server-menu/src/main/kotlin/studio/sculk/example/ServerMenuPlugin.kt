package studio.sculk.example

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlatform

public class ServerMenuPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private lateinit var settings: MenuSettings
    private lateinit var menus: ServerMenus

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                config()
                gui()
            }

        settings = sculk.config.load()
        menus = ServerMenus { settings }

        sculk.commands.register(
            command("menu") {
                description = "Open the server menu."
                player {
                    menus.main().openFor(player ?: return@player)
                }
            },
        )
    }

    override fun onDisable() {
        if (::sculk.isInitialized) sculk.close()
    }
}
