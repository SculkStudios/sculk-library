package studio.sculk.example.economy

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import studio.sculk.command.paper.commands
import studio.sculk.config.ConfigFile
import studio.sculk.config.SculkConfig
import studio.sculk.event.listen
import studio.sculk.execution.ExecutionContext
import studio.sculk.getOrThrow
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.placeholder.Refresh
import studio.sculk.task.repeating
import studio.sculk.text.Text
import java.util.Locale
import java.util.UUID

/**
 * A complete economy on Sculk: typed config, permission descriptors, commands with help, flags
 * and confirmation, a balance placeholder, join handling and autosave. It runs unchanged on Paper,
 * Folia and Canvas.
 */
class EconomyPlugin : SculkPaperPlugin() {
    private lateinit var settings: ConfigFile<Settings>
    private lateinit var saved: ConfigFile<SavedBalances>
    private lateinit var ledger: Ledger

    override suspend fun start() {
        settings = SculkConfig.load<Settings>(dataPath.resolve("config.yml")) {
            header = listOf("Economy example for Sculk Studio.")
        }.getOrThrow()
        saved = SculkConfig.load<SavedBalances>(dataPath.resolve("balances.yml")).getOrThrow()
        ledger = saved.value.toLedger(settings.value.startingBalance)

        sculk.permissions.register(EconomyPermissions)
        val commands = EconomyCommands(ledger, ServerPlayers, settings::value)
        sculk.commands().register(*commands.all.toTypedArray())

        val placeholders = PlaceholderRegistry(sculk.scope)
        placeholders.audience("economy:balance", Refresh.Live) {
            uniqueId?.let { id -> Component.text(String.format(Locale.ROOT, "%,.2f %s", ledger.balance(id), settings.value.currency)) }
        }
        val text = Text()

        sculk.events.listen<PlayerJoinEvent> { event ->
            val player = event.player
            if (ledger.open(player.uniqueId)) {
                player.sendMessage(text.render("<primary>Welcome! You start with <economy_balance>.", placeholders.resolver(player)))
            }
        }

        sculk.scope.repeating(periodTicks = settings.value.autosaveMinutes * TICKS_PER_MINUTE, context = ExecutionContext.Async) {
            save()
        }
    }

    // Runs while the server stops, after which nothing scheduled would run, so it saves directly.
    override fun stop() {
        if (::ledger.isInitialized) save()
    }

    private fun save() {
        saved.save(SavedBalances.of(ledger)).getOrThrow()
    }

    /** Online players first, then anyone the server has cached; never a blocking profile lookup. */
    private object ServerPlayers : Players {
        override fun find(name: String): Holder? {
            Bukkit.getPlayerExact(name)?.let { return Holder(it.uniqueId, it.name) }
            val cached = Bukkit.getOfflinePlayerIfCached(name) ?: return null
            return Holder(cached.uniqueId, cached.name ?: name)
        }

        override fun name(id: UUID): String? = Bukkit.getOfflinePlayer(id).name

        override fun names(): Collection<String> = Bukkit.getOnlinePlayers().map { it.name }
    }

    private companion object {
        const val TICKS_PER_MINUTE = 1_200L
    }
}
