package studio.sculk.example.economy

import kotlinx.serialization.Serializable
import studio.sculk.command.command
import studio.sculk.data.Id
import studio.sculk.data.Index
import studio.sculk.data.SculkCache
import studio.sculk.data.SculkRepository
import studio.sculk.data.Table
import studio.sculk.data.cached
import studio.sculk.platform.SculkPlugin
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle

/**
 * The example that touches SQL: an entity, a repository, a cache, a leaderboard and a transaction.
 */
@Serializable
@Table("balances")
data class Balance(@Id val uuid: String, @Index val name: String = "", val coins: Long = 0)

class EconomyService(private val cache: SculkCache<Balance, String>, private val repository: SculkRepository<Balance, String>) {
    suspend fun balanceOf(uuid: String, name: String): Balance = cache.findOrCreate(uuid) { Balance(uuid = it, name = name) }.getOrThrow()

    suspend fun give(uuid: String, name: String, amount: Long): Balance {
        val updated = balanceOf(uuid, name).let { it.copy(coins = it.coins + amount) }
        cache.save(updated).getOrThrow()
        return updated
    }

    /** Sorted and limited in SQL, not by loading the table and sorting it in memory. */
    suspend fun top(rows: Int): List<Balance> = repository.topBy("coins", rows).getOrThrow()
}

class EconomyPlugin : SculkPlugin() {
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Gradient(listOf("#f1fa8c", "#ffb86c")),
            "danger" to ThemeStyle.Solid("#ff5f5f"),
            "dim" to ThemeStyle.Solid("#6272a4"),
        ),
    )

    override fun setup() {
        // Touching sculk.data is what opens the pool; a plugin that never does pays nothing.
        val repository = sculk.data.repository<Balance, String>()
        val economy = sculk.services.register(EconomyService(repository.cached(Balance::uuid), repository))

        +command("balance") {
            description = "Shows your balance."
            player {
                val viewer = player!!
                // Suspending straight from a command handler: it already runs in the plugin scope.
                val balance = economy.balanceOf(viewer.uniqueId.toString(), viewer.name)
                reply("<value><coins></value> coins.", "coins" to balance.coins.toString())
            }
        }

        +command("pay") {
            description = "Gives coins to a player."
            permission = "example.pay"
            player("target")
            long("amount", min = 1)
            player {
                val target = argument<org.bukkit.entity.Player>("target")
                val amount = argument<Long>("amount")
                val updated = economy.give(target.uniqueId.toString(), target.name, amount)
                reply(
                    "Gave <value><amount></value> to <value><name></value>.",
                    "amount" to amount.toString(),
                    "name" to target.name,
                )
                sculk.messages.send(target, "You now have <value><coins></value>.", "coins" to updated.coins.toString())
            }
        }

        +command("baltop") {
            description = "Shows the richest players."
            executes {
                val rows = economy.top(10)
                if (rows.isEmpty()) {
                    reply("<dim>Nobody has any coins yet.</dim>")
                    return@executes
                }
                rows.forEachIndexed { index, balance ->
                    reply(
                        "<dim><rank>.</dim> <value><name></value> — <value><coins></value>",
                        "rank" to (index + 1).toString(),
                        "name" to balance.name,
                        "coins" to balance.coins.toString(),
                    )
                }
            }
        }
    }
}
