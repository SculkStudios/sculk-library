package studio.sculk.itest.hud

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import studio.sculk.itest.bot.BotDriver
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.placeholder.Refresh
import studio.sculk.presentation.Huds
import studio.sculk.presentation.hud
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Shows a HUD to the test client and checks what it sees: every surface, a placeholder change, the
 * sidebar and nametag yielding to another plugin's board and team, titles, and closing. Writes `results.properties` and stops the
 * server.
 */
class HudCheckPlugin : SculkPaperPlugin(), Listener {
    private val results = sortedMapOf<String, String>()
    private val joined = CompletableFuture<Player>()

    @Volatile private var coins = 10

    override suspend fun start() {
        val placeholders = PlaceholderRegistry(sculk.scope)
        val coinsTag = placeholders.global("itest:coins", Refresh.UntilInvalidated) { coins }
        val huds = Huds(this, sculk.scope.resources, placeholders)
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "hud-check") { check(huds, coinsTag::invalidate) } }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        joined.complete(event.player)
    }

    private fun check(huds: Huds, changed: () -> Unit) {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                bot.await("JOINED", 60)
                val player = joined.get(30, TimeUnit.SECONDS)
                val sidebar = huds.sidebarAvailable
                val layout = hud {
                    actionBar("Coins <itest_coins>")
                    bar("event", "Event <itest_coins>", progress = { 0.5f })
                    tabList("Header <itest_coins>", "Footer")
                    if (sidebar) {
                        sidebar("Sculk", "Coins <itest_coins>", "Static line")
                        nametag(prefix = "[<itest_coins>]", suffix = "*")
                        belowName("Coins <itest_coins>")
                    }
                }
                lateinit var shown: studio.sculk.presentation.PlayerHud
                verdict("surfaces-show") {
                    if (!sidebar) {
                        val refused = huds.show(player, hud { sidebar("Sculk", "Line") })
                        check(refused is SculkFailure.UnsupportedCapability) { "a sidebar on this server gave $refused" }
                        val noNames = huds.show(player, hud { nametag("x") })
                        check(noNames is SculkFailure.UnsupportedCapability) { "a nametag on this server gave $noNames" }
                    }
                    shown = huds.show(player, layout).getOrThrow()
                    bot.await("ACTIONBAR") { it.fields[0] == "Coins 10" }
                    bot.await("BOSSBAR") { it.fields[1] == "ADD" && it.fields[2] == "Event 10" && it.fields[3] == "0.5" }
                    bot.await("TAB") { it.fields[0] == "Header 10" && it.fields[1] == "Footer" }
                    if (sidebar) {
                        bot.await("SIDEBAR") { it.fields[0] == "Sculk" && it.fields[1] == "Coins 10|Static line" }
                        bot.awaitState("NAMETAG", "SculkBot") { it.fields == listOf("SculkBot", "[10]", "*") }
                        bot.awaitState("BELOWNAME", "SculkBot") { it.fields == listOf("SculkBot", "Coins 10") }
                    }
                    true to (if (sidebar) "all six" else "three; no scoreboards")
                }
                verdict("a-change-redraws-what-used-it") {
                    coins = 11
                    changed()
                    bot.await("BOSSBAR") { it.fields[1] == "UPDATE_TITLE" && it.fields[2] == "Event 11" }
                    bot.await("TAB") { it.fields[0] == "Header 11" }
                    if (sidebar) {
                        bot.await("SIDEBAR") { it.fields[1] == "Coins 11|Static line" }
                        bot.awaitState("NAMETAG", "SculkBot") { it.fields == listOf("SculkBot", "[11]", "*") }
                        bot.awaitState("BELOWNAME", "SculkBot") { it.fields == listOf("SculkBot", "Coins 11") }
                    }
                    true to "redrawn"
                }
                if (sidebar) {
                    verdict("nametag-yields-to-another-team") {
                        onGlobal { Bukkit.getScoreboardManager().mainScoreboard.registerNewTeam("foreign").addEntry(player.name) }
                        coins = 12
                        changed()
                        val tookIt = bot.sees("NAMETAG", 2_500) { it.fields[1] == "[12]" }
                        onGlobal { Bukkit.getScoreboardManager().mainScoreboard.getTeam("foreign")?.unregister() }
                        val after = bot.await("NAMETAG") { it.fields[1] == "[12]" }
                        (!tookIt) to "took the player from another team: $tookIt, then $after"
                    }
                } else {
                    results["nametag-yields-to-another-team"] = "SKIP: Folia has no scoreboards"
                }
                if (sidebar) {
                    verdict("sidebar-yields-to-another-board") {
                        shown.close()
                        bot.await("SIDEBAR") { it.fields[0].isEmpty() }
                        val foreign = onGlobal { foreignBoard() }
                        onPlayer(player) { player.scoreboard = foreign }
                        bot.await("SIDEBAR") { it.fields[0] == "Foreign" }
                        shown = huds.show(player, layout).getOrThrow()
                        val tookIt = bot.sees("SIDEBAR", 2_500) { it.fields[0] == "Sculk" }
                        onPlayer(player) { player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard }
                        val after = bot.await("SIDEBAR") { it.fields[0] == "Sculk" }
                        (!tookIt) to "took the foreign board: $tookIt, then $after"
                    }
                } else {
                    results["sidebar-yields-to-another-board"] = "SKIP: Folia has no scoreboards"
                }
                verdict("titles-render-placeholders") {
                    huds.title(player, "Round <itest_coins>", "Good luck")
                    bot.await("TITLE") { it.fields[0] == "Round $coins" && it.fields[1] == "Good luck" }
                    huds.clearTitle(player)
                    bot.await("TITLECLEARED")
                    true to "shown and cleared"
                }
                verdict("closing-removes-everything") {
                    shown.close()
                    bot.await("BOSSBAR") { it.fields[1] == "REMOVE" }
                    bot.await("TAB") { it.fields[0].isEmpty() && it.fields[1].isEmpty() }
                    if (sidebar) {
                        bot.await("SIDEBAR") { it.fields[0].isEmpty() }
                        bot.awaitState("NAMETAG", "SculkBot") { it.fields == listOf("SculkBot", "", "") }
                        bot.awaitState("BELOWNAME", "SculkBot") { it.fields == listOf("SculkBot", "") }
                    }
                    true to "removed"
                }
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("surfaces-show", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
    }

    // Another plugin's board with its own sidebar; Canvas wants scoreboard writes on the global tick.
    private fun foreignBoard(): Scoreboard = Bukkit.getScoreboardManager().newScoreboard.also { board ->
        board.registerNewObjective("foreign", Criteria.DUMMY, Component.text("Foreign")).apply {
            setDisplaySlot(DisplaySlot.SIDEBAR)
            getScore("x").score = 1
        }
    }

    private fun <T> onGlobal(action: () -> T): T {
        val result = CompletableFuture<T>()
        server.globalRegionScheduler.run(this) { result.complete(action()) }
        return result.get(10, TimeUnit.SECONDS)
    }

    private fun onPlayer(player: Player, action: () -> Unit) {
        val done = CompletableFuture<Unit>()
        player.scheduler.run(this, {
            action()
            done.complete(Unit)
        }, null)
        done.get(10, TimeUnit.SECONDS)
    }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }
}
