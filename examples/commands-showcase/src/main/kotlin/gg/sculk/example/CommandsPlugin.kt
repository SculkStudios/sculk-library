package gg.sculk.example

import gg.sculk.core.adventure.reply
import gg.sculk.core.command.CommandBuilder
import gg.sculk.core.command.argument.ArgumentParser
import gg.sculk.core.command.command
import gg.sculk.platform.SculkPlatform
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin

public class CommandsPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {}

        sculk.commands.registerAll(
            toolsCommand(),
            worldCommand(),
        )
    }

    override fun onDisable(): Unit = sculk.close()
}

// ---------------------------------------------------------------------------
// /tools — demonstrates all built-in argument types
// ---------------------------------------------------------------------------

private fun toolsCommand(): CommandBuilder =
    command("tools") {
        description = "Commands showcasing all argument types."
        permission = "showcase.tools"

        // /tools greet <target> [message]  — player arg + greedy string
        sub("greet") {
            player("target")
            greedy("message")
            player {
                val target = argument<org.bukkit.entity.Player>("target")
                val message = argumentOrNull<String>("message") ?: "Hello!"
                target.reply("<aqua>${player!!.name} <white>says: <gray>$message")
                reply("<green>Greeted <white>${target.name}<green>.")
                playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)
            }
        }

        // /tools give <target> <amount>  — player arg + int arg
        sub("give") {
            player("target")
            int("amount")
            player {
                val target = argument<org.bukkit.entity.Player>("target")
                val amount = argument<Int>("amount")
                reply("<green>Gave <white>$amount <green>coins to <white>${target.name}<green>.")
                title(
                    title = "<gold>+$amount Coins",
                    subtitle = "<gray>from ${player!!.name}",
                    stay = 40,
                )
            }
        }

        // /tools toggle <setting> <enabled>  — choice arg + boolean arg
        sub("toggle") {
            choice("setting", "pvp", "fly", "nametags")
            boolean("enabled")
            executes {
                val setting = argument<String>("setting")
                val enabled = argument<Boolean>("enabled")
                val state = if (enabled) "<green>enabled" else "<red>disabled"
                reply("<gray>$setting is now $state<gray>.")
            }
        }

        // /tools precise <multiplier>  — double arg
        sub("precise") {
            double("multiplier")
            executes {
                val multiplier = argument<Double>("multiplier")
                reply("<yellow>Multiplier set to <white>${multiplier}x<yellow>.")
            }
        }

        // /tools bignum <id>  — long arg
        sub("bignum") {
            long("id")
            executes {
                val id = argument<Long>("id")
                reply("<gray>ID: <white>$id")
            }
        }
    }

// ---------------------------------------------------------------------------
// /world <name> — demonstrates a custom ArgumentParser<World>
// ---------------------------------------------------------------------------

/** Parses a world name into a loaded [World]. Tab-completes loaded world names. */
private object WorldParser : ArgumentParser<World> {
    override val typeName = "world"

    override fun parse(input: String): World? = Bukkit.getWorld(input)

    override fun suggest(input: String): List<String> =
        Bukkit.getWorlds().map { it.name }.filter { it.startsWith(input, ignoreCase = true) }
}

private fun worldCommand(): CommandBuilder =
    command("world") {
        description = "Teleport to a world's spawn."
        permission = "showcase.world"
        argument("world", WorldParser)
        player {
            val world = argument<World>("world")
            player!!.teleport(world.spawnLocation)
            reply("<green>Teleported to <white>${world.name}<green>.")
            actionbar("<gray>Now in: <white>${world.name}")
        }
    }
