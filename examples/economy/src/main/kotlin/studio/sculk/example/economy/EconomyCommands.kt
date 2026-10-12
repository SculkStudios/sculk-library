package studio.sculk.example.economy

import studio.sculk.command.Arguments
import studio.sculk.command.CommandContext
import studio.sculk.command.CommandSpec
import studio.sculk.command.command
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** A player the economy knows by name, online or not. */
data class Holder(val id: UUID, val name: String)

/** Looks players up by name. Paper backs it with the server; tests back it with a map. */
interface Players {
    fun find(name: String): Holder?

    /** The last name the player was seen with, or null for an id the server never saw. */
    fun name(id: UUID): String?

    fun names(): Collection<String>
}

/**
 * The plugin's commands, built from the ledger and a player directory and nothing from the server,
 * so the same specs run on Paper and in unit tests through `CommandDispatcher`.
 */
class EconomyCommands(private val ledger: Ledger, private val players: Players, private val settings: () -> Settings) {
    val balance: CommandSpec = command("balance", "bal") {
        description = "Show a balance"
        permission(EconomyPermissions.balance)
        val name = word("player").optional().suggests { partial, _ -> players.names().filter { it.startsWith(partial, ignoreCase = true) } }
        executes {
            val holder =
                this[name]?.let { target ->
                    players.find(target) ?: fail("<danger>Nobody called <name> has played here.", "name" to target)
                }
                    ?: senderHolder()
            reply(
                "<primary><name></primary> has <value><amount></value>.",
                "name" to holder.name,
                "amount" to money(ledger.balance(holder.id)),
            )
        }
    }

    val pay: CommandSpec = command("pay") {
        description = "Send money to another player"
        permission(EconomyPermissions.pay)
        val target = word("player").suggests { partial, _ -> players.names().filter { it.startsWith(partial, ignoreCase = true) } }
        val amount = double("amount", min = 0.01)
        player {
            val sender = senderHolder()
            val receiver = players.find(this[target]) ?: fail("<danger>Nobody called <name> has played here.", "name" to this[target])
            if (receiver.id == sender.id) fail("<danger>You cannot pay yourself.")
            if (!ledger.transfer(sender.id, receiver.id, this[amount])) {
                fail("<danger>You only have <amount>.", "amount" to money(ledger.balance(sender.id)))
            }
            reply("<success>Sent <amount> to <name>.", "amount" to money(this[amount]), "name" to receiver.name)
        }
    }

    val economy: CommandSpec = command("economy", "eco") {
        description = "Manage balances"
        permission(EconomyPermissions.admin)
        help()
        literal("give", "add") {
            val target = word("player")
            val amount = double("amount", min = 0.01)
            val silent = flag("silent", short = 's')
            executes {
                val holder = holder(this[target])
                ledger.deposit(holder.id, this[amount])
                if (!this[silent]) reply("<success>Gave <amount> to <name>.", "amount" to money(this[amount]), "name" to holder.name)
            }
        }
        literal("take") {
            val target = word("player")
            val amount = double("amount", min = 0.01)
            executes {
                val holder = holder(this[target])
                if (!ledger.withdraw(holder.id, this[amount])) {
                    fail(
                        "<danger><name> only has <amount>.",
                        "name" to holder.name,
                        "amount" to money(ledger.balance(holder.id)),
                    )
                }
                reply("<success>Took <amount> from <name>.", "amount" to money(this[amount]), "name" to holder.name)
            }
        }
        literal("reset") {
            confirm(10.seconds)
            executes {
                ledger.reset()
                reply("<warning>Every balance was reset.")
            }
        }
        literal("top") {
            val size = flag("size", Arguments.integer(1, 20), short = 'n')
            executes {
                val ranked = ledger.snapshot().entries.sortedByDescending { it.value }.take(this[size] ?: 5)
                if (ranked.isEmpty()) fail("<muted>Nobody has an account yet.")
                ranked.forEachIndexed { index, (id, amount) ->
                    reply(
                        "<muted><rank>.</muted> <name> <value><amount></value>",
                        "rank" to index + 1,
                        "name" to (players.name(id) ?: id.toString()),
                        "amount" to money(amount),
                    )
                }
            }
        }
    }

    val all: List<CommandSpec> get() = listOf(balance, pay, economy)

    private fun CommandContext.senderHolder(): Holder {
        val id = source.uniqueId ?: fail("<danger>Name a player: /<command> [player].", "command" to label)
        return Holder(id, source.name)
    }

    private fun CommandContext.holder(name: String): Holder =
        players.find(name) ?: fail("<danger>Nobody called <name> has played here.", "name" to name)

    private fun money(amount: Double): String = String.format(Locale.ROOT, "%,.2f %s", amount, settings().currency)
}
