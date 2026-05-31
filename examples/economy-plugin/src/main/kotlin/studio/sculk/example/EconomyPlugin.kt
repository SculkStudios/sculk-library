package studio.sculk.example

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import studio.sculk.SculkResult
import studio.sculk.adventure.reply
import studio.sculk.command.command
import studio.sculk.data.cache.SculkCache
import studio.sculk.platform.SculkPlugin
import java.time.Duration
import java.util.UUID

public class EconomyPlugin :
    SculkPlugin({
        config()
        data()
    }) {
    private lateinit var settings: EconomySettings
    private lateinit var service: EconomyService

    override fun setup() {
        settings = sculk.config.load()
        val repository = sculk.data.repository<EconomyAccount, UUID>()
        val cached: SculkCache<EconomyAccount, UUID> =
            sculk.data.cached(repository, EconomyAccount::uuid) {
                ttl = Duration.ofMinutes(10)
                maxSize = 10_000
            }
        service = EconomyService(cached, { settings.startingCoins })

        sculk.commands.register(economyCommand())
    }

    override fun shutdown() {
        if (::service.isInitialized) {
            kotlinx.coroutines.runBlocking { service.flushKnown().logFailure("Failed to flush economy accounts") }
        }
    }

    private fun economyCommand() =
        command("coins") {
            description = "View, pay, and manage player coins."
            aliases = listOf("money", "balance")
            permission = "economy.coins"
            player("target", optional = true)

            executes {
                val target = argumentOrNull<Player>("target") ?: player
                if (target == null) {
                    reply("<red>Console must specify a player.")
                    return@executes
                }
                showBalance(sender, target)
            }

            sub("pay") {
                permission = "economy.coins.pay"
                player("target")
                long("amount", min = 1)
                player {
                    val payer = player ?: return@player
                    val target = argument<Player>("target")
                    val amount = argument<Long>("amount")
                    runAsync(sender) {
                        service.transfer(payer.uniqueId, payer.name, target.uniqueId, target.name, amount)
                    }.sync { result ->
                        when (result) {
                            is SculkResult.Success -> {
                                val paidMessage =
                                    settings.messages.paid
                                        .replace("<amount>", amount.toString())
                                        .replace("<player>", target.name)
                                val receivedMessage =
                                    settings.messages.received
                                        .replace("<amount>", amount.toString())
                                        .replace("<player>", payer.name)
                                reply(paidMessage)
                                target.reply(receivedMessage)
                            }
                            is SculkResult.Failure -> reply("<red>${result.message}")
                        }
                    }
                }
            }

            sub("give") {
                permission = "economy.admin"
                player("target")
                long("amount", min = 1)
                executes {
                    mutateBalance(sender, argument("target"), argument("amount"), service::deposit, "Gave")
                }
            }

            sub("take") {
                permission = "economy.admin"
                player("target")
                long("amount", min = 1)
                executes {
                    mutateBalance(sender, argument("target"), argument("amount"), service::withdraw, "Took")
                }
            }

            sub("set") {
                permission = "economy.admin"
                player("target")
                long("amount", min = 0)
                executes {
                    mutateBalance(sender, argument("target"), argument("amount"), service::set, "Set")
                }
            }

            sub("top") {
                executes {
                    runAsync(sender) { service.top(settings.topLimit) }.sync { result ->
                        when (result) {
                            is SculkResult.Success -> {
                                reply("<gold><bold>Top balances</bold>")
                                result.value.forEachIndexed { index, account ->
                                    reply("<yellow>${index + 1}. <aqua>${account.name}</aqua> <gray>- <white>${account.coins}")
                                }
                            }
                            is SculkResult.Failure -> reply("<red>${result.message}")
                        }
                    }
                }
            }

            sub("reload") {
                permission = "economy.reload"
                executes {
                    settings = sculk.config.load()
                    reply(settings.messages.reloaded)
                }
            }
        }

    private fun showBalance(
        sender: CommandSender,
        target: Player,
    ) {
        runAsync(sender) { service.balance(target.uniqueId, target.name) }.sync { result ->
            when (result) {
                is SculkResult.Success -> {
                    val message =
                        if (sender == target) settings.messages.balance else settings.messages.otherBalance.replace("<player>", target.name)
                    sender.reply(message.replace("<coins>", result.value.coins.toString()))
                }
                is SculkResult.Failure -> sender.reply("<red>${result.message}")
            }
        }
    }

    private fun mutateBalance(
        sender: CommandSender,
        target: Player,
        amount: Long,
        operation: suspend (UUID, String, Long) -> SculkResult<EconomyAccount>,
        verb: String,
    ) {
        runAsync(sender) { operation(target.uniqueId, target.name, amount) }.sync { result ->
            when (result) {
                is SculkResult.Success ->
                    sender.reply(
                        "<green>$verb <yellow>$amount</yellow> coins for <aqua>${target.name}</aqua>. " +
                            "New balance: <yellow>${result.value.coins}</yellow>.",
                    )
                is SculkResult.Failure -> sender.reply("<red>${result.message}")
            }
        }
    }

    private fun <T> runAsync(
        sender: CommandSender,
        block: suspend () -> SculkResult<T>,
    ): AsyncReply<T> = AsyncReply(sender, block)

    private inner class AsyncReply<T>(
        private val sender: CommandSender,
        private val block: suspend () -> SculkResult<T>,
    ) {
        fun sync(consumer: CommandSender.(SculkResult<T>) -> Unit) {
            sculk.scope.launchAsync {
                val result = block()
                sculk.scheduler.runSync {
                    sender.consumer(result)
                }
            }
        }
    }

    private fun SculkResult<*>.logFailure(prefix: String) {
        if (this is SculkResult.Failure) logger.warning("$prefix: $message")
    }
}
