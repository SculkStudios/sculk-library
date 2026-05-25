package studio.sculk.example

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.config.annotation.ConfigFile
import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

public class EconomyPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform
    private lateinit var settings: EconomySettings
    private val balances = ConcurrentHashMap<UUID, Long>()

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                config()
            }
        settings = sculk.config.load()

        sculk.commands.register(
            command("coins") {
                description = "View and manage coins."
                aliases = listOf("money", "balance")

                player {
                    val balance = balances.computeIfAbsent(player!!.uniqueId) { settings.startingCoins }
                    reply(settings.balanceMessage.replace("<coins>", balance.toString()))
                }

                sub("give") {
                    permission = "economy.admin"
                    player("target")
                    long("amount", min = 1)
                    executes {
                        val target = argument<org.bukkit.entity.Player>("target")
                        val amount = argument<Long>("amount")
                        val newBalance = balances.merge(target.uniqueId, amount, Long::plus) ?: amount
                        reply("<green>Gave <yellow>$amount</yellow> coins to <aqua>${target.name}</aqua>.")
                        target.sendMessage("Your balance is now $newBalance coins.")
                    }
                }

                sub("take") {
                    permission = "economy.admin"
                    player("target")
                    long("amount", min = 1)
                    executes {
                        val target = argument<org.bukkit.entity.Player>("target")
                        val amount = argument<Long>("amount")
                        val current = balances.computeIfAbsent(target.uniqueId) { settings.startingCoins }
                        balances[target.uniqueId] = max(0, current - amount)
                        reply("<green>Took <yellow>$amount</yellow> coins from <aqua>${target.name}</aqua>.")
                    }
                }
            },
        )
    }

    override fun onDisable() {
        sculk.close()
    }
}

@ConfigFile("economy.yml")
public data class EconomySettings(
    val startingCoins: Long = 100,
    val balanceMessage: String = "<gold>You have <yellow><coins></yellow> coins.",
)
