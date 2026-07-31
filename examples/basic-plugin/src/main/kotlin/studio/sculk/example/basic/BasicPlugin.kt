package studio.sculk.example.basic

import kotlinx.serialization.Serializable
import org.bukkit.Material
import studio.sculk.command.command
import studio.sculk.config.Comment
import studio.sculk.config.ConfigFile
import studio.sculk.config.Min
import studio.sculk.gui.gui
import studio.sculk.platform.SculkPlugin
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle

/**
 * The vertical slice: a theme, a config, a command, a menu, a service.
 *
 * Everything the docs link to when they say "start here". Deliberately small enough to read in one
 * sitting and complete enough that nothing here is a lie by omission.
 */
@Serializable
@ConfigFile("settings.yml")
@Comment("The example plugin's settings.")
data class Settings(
    @Comment("How many diamonds the /welcome menu offers.")
    @Min(1)
    val giftAmount: Int = 3,
    @Comment("Shown to a player the first time they run /welcome.")
    val greeting: String = "<value>Welcome, <name>!</value>",
)

/** A plugin's own service. Registered so the rest of the plugin can reach it by type. */
class GiftService(private val amount: Int) {
    fun giftsFor(name: String): Int = amount

    val perPlayer: Int get() = amount
}

class BasicPlugin : SculkPlugin() {
    // Messages are written against meaning, not colour: changing what "danger" looks like is one
    // edit here rather than a search for every red string.
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "dim" to ThemeStyle.Solid("#6272a4"),
        ),
    )

    override fun setup() {
        val settings = sculk.config.load<Settings>().getOrThrow()
        val gifts = sculk.services.register(GiftService(settings.giftAmount))

        +command("welcome") {
            description = "Opens the welcome menu."
            player {
                val viewer = player!!
                // The player's name goes in as a placeholder value, never substituted into the
                // template — it is text the player controls.
                reply(settings.greeting, "name" to viewer.name)

                sculk.menus.open(
                    gui("<value>Welcome</value>") {
                        size = 27
                        border(Material.GRAY_STAINED_GLASS_PANE) { name = " " }
                        item(13) {
                            material = Material.DIAMOND
                            amount = gifts.perPlayer
                            name = "<value>A gift</value>"
                            lore("<dim>Click to close.")
                            onClick { close() }
                        }
                    },
                    viewer,
                )
            }
        }

        +command("gifts") {
            description = "Says how many gifts a player gets."
            executes {
                // Reached by type: no string keys, so a rename is a compile error.
                reply("<value><count></value> per player.", "count" to sculk.services.get<GiftService>().perPlayer.toString())
            }
        }
    }
}
