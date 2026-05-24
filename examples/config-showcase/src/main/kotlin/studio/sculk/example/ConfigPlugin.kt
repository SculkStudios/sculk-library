package studio.sculk.example

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.annotation.Max
import studio.sculk.config.annotation.Min
import studio.sculk.config.annotation.NotEmpty
import studio.sculk.core.command.CommandBuilder
import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlatform

// ---------------------------------------------------------------------------
// Typed config definition — annotate constraints on each field
// ---------------------------------------------------------------------------

@ConfigFile("settings.yml")
public data class Settings(
    @NotEmpty val prefix: String = "<gray>[<aqua>Demo<gray>]",
    @Min(1) @Max(20) val maxHomes: Int = 5,
    val welcomeMessage: String = "<green>Welcome to the server!",
    val allowFlight: Boolean = false,
)

// ---------------------------------------------------------------------------
// Plugin entry point
// ---------------------------------------------------------------------------

public class ConfigPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private lateinit var settings: Settings

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                config()
            }

        // Load config — creates settings.yml with defaults on first run.
        // sculk.config is non-null because config() was called above.
        settings = sculk.config!!.load<Settings>()

        // Re-read settings whenever /reload is run so handlers see the latest values.
        sculk.config!!.onReload<Settings> {
            settings = sculk.config!!.load<Settings>()
            logger.info("Settings reloaded — maxHomes=${settings.maxHomes}")
        }

        sculk.commands.registerAll(
            infoCommand(),
            reloadCommand(),
        )
    }

    override fun onDisable(): Unit = sculk.close()

    // /info — shows current config values to the player
    private fun infoCommand(): CommandBuilder =
        command("info") {
            description = "Show current config values."
            player {
                reply("${settings.prefix} <gray>Config values:")
                reply("<gray>• maxHomes: <white>${settings.maxHomes}")
                reply("<gray>• allowFlight: <white>${settings.allowFlight}")
                reply("<gray>• welcomeMessage: <white>${settings.welcomeMessage}")
            }
        }

    // /config reload — reloads all config files from disk
    private fun reloadCommand(): CommandBuilder =
        command("config") {
            description = "Reload the plugin config."
            permission = "demo.admin"
            sub("reload") {
                executes {
                    sculk.config!!.reloadAll()
                    reply("${settings.prefix} <green>Config reloaded.")
                }
            }
        }
}
