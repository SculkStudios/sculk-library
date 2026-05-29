package studio.sculk.example

import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlugin

public class ServerMenuPlugin :
    SculkPlugin({
        config()
        gui()
    }) {
    private lateinit var settings: MenuSettings
    private lateinit var menus: ServerMenus

    override fun setup() {
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
}
