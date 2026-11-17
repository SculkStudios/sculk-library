package studio.sculk.itest.examples

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.command.CommandException
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.itest.bot.BotDriver
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the example plugins the way people would: the economy through the console, reading its
 * generated files, permissions and replies, and the HUD, shop and staff examples as a player, with
 * the real test client. Writes `results.properties` and stops the server.
 */
class ExampleCheckPlugin : JavaPlugin(), Listener {
    private val results = ConcurrentHashMap<String, String>()
    private val joined = CompletableFuture<Player>()

    // Runs before any world or example plugin loads its data: every run starts as a fresh server.
    override fun onLoad() {
        val plugins = dataFolder.parentFile
        listOf("SculkEconomy", "SculkHudExample", "SculkShopExample", "SculkStaffExample").forEach { File(plugins, it).deleteRecursively() }
        // Player data moved from world/playerdata to world/players in 26.1.
        listOf("world/playerdata", "world/players").forEach { File(server.worldContainer, it).deleteRecursively() }
    }

    override fun onEnable() {
        // Server folders are reused between runs; the client starts as a plain member every time.
        server.getOfflinePlayerIfCached("SculkBot")?.isOp = false
        server.pluginManager.registerEvents(this, this)

        // The first global tick runs after every plugin has enabled and the command tree is built.
        server.globalRegionScheduler.runDelayed(this, { check() }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        joined.complete(event.player)
    }

    private fun check() {
        val economy = server.pluginManager.getPlugin("SculkEconomy")
        verdict("economy-enabled") { (economy?.isEnabled == true) to "SculkEconomy enabled: ${economy?.isEnabled}" }
        val folder = economy?.dataFolder ?: File("plugins/SculkEconomy")
        verdict("config-generated-with-comments") {
            val text = File(folder, "config.yml").takeIf(File::isFile)?.readText().orEmpty()
            ("currency: coins" in text && "# What a new account starts with." in text) to "config.yml was\n$text"
        }
        verdict("permissions-registered-with-defaults") {
            val pay = server.pluginManager.getPermission("economy.pay")
            val admin = server.pluginManager.getPermission("economy.admin")
            (pay?.default == PermissionDefault.TRUE && admin?.default == PermissionDefault.OP) to "pay ${pay?.default}, admin ${admin?.default}"
        }
        verdict("help-lists-the-admin-forms") {
            val replies = run("eco help")
            (replies.firstOrNull() == "/economy can be used as:" && "/economy give <player> <amount> [--silent]" in replies) to "replies $replies"
        }
        verdict("console-is-asked-to-name-a-player") {
            val replies = run("balance")
            (replies == listOf("Name a player: /balance [player].")) to "replies $replies"
        }
        verdict("payments-are-for-players-only") {
            val replies = run("pay Someone 5")
            (replies == listOf("Only players can run this command.")) to "replies $replies"
        }
        verdict("an-empty-economy-has-no-top-list") {
            val replies = run("eco top --size 3")
            (replies == listOf("Nobody has an account yet.")) to "replies $replies"
        }
        verdict("reset-asks-for-confirmation") {
            val first = run("eco reset")
            val second = run("eco reset")
            (first == listOf("Run the command again within 10s to confirm.") && second == listOf("Every balance was reset.")) to
                "first $first, then $second"
        }
        // A player blocks while the client answers, which the global tick must never do.
        thread(name = "example-check") {
            asPlayer()
            write()
            server.globalRegionScheduler.run(this) { server.shutdown() }
        }
    }

    private fun asPlayer() {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                bot.await("JOINED", 60)
                val player = runCatching { joined.get(30, TimeUnit.SECONDS) }.getOrElse { error("no PlayerJoinEvent within 30s") }
                // A test client never eats or dodges; it must not die halfway through a check.
                onPlayer(player) {
                    player.isOp = true
                    player.isInvulnerable = true
                    player.foodLevel = 20
                    player.saturation = 20f
                }
                val scoreboards = server.pluginManager.getPlugin("SculkHudExample")?.let { hasScoreboards() } ?: false
                val dialogs = runCatching { Class.forName("io.papermc.paper.dialog.Dialog") }.isSuccess

                verdict("hud-shows-on-join") {
                    bot.awaitState("TITLE", "Welcome, SculkBot") { true }
                    bot.await("BOSSBAR") { it.fields[1] == "ADD" && it.fields[2].startsWith("Event ends in") }
                    if (scoreboards) bot.awaitState("SIDEBAR", "Sculk Network") { "Rank: Member" in it.fields[1].split('|') }
                    true to "title, boss bar" + if (scoreboards) " and sidebar" else "; no sidebar without scoreboards"
                }
                verdict("hud-turns-off-and-on") {
                    bot.command("/hud")
                    bot.await("BOSSBAR") { it.fields[1] == "REMOVE" }
                    bot.await("CHAT") { it.fields[0] == "Your HUD is off. /hud brings it back." }
                    bot.command("/hud")
                    bot.await("BOSSBAR") { it.fields[1] == "ADD" }
                    // Op since joining: showing the HUD again reads the rank afresh.
                    if (scoreboards) bot.awaitState("SIDEBAR", "Sculk Network") { "Rank: Admin" in it.fields[1].split('|') }
                    true to "off, then on" + if (scoreboards) " with the new rank" else ""
                }
                verdict("shop-sells-for-emeralds") {
                    onPlayer(player) {
                        player.inventory.clear()
                        player.inventory.addItem(ItemStack(Material.EMERALD, 5))
                    }
                    bot.command("/shop")
                    bot.await("WINDOW") { it.fields[2] == "Shop" }
                    bot.click(0)
                    val said = if (dialogs) {
                        bot.await("DIALOG") { it.fields[0] == "How many?" }
                        bot.answerDialog("Buy", "quantity" to 2)
                        bot.await("CHAT") { it.fields[0].startsWith("Bought") }
                    } else {
                        bot.await("WINDOW") { it.fields[2] == "Buy one?" }
                        bot.click(2)
                        bot.await("CHAT") { it.fields[0].startsWith("Bought") }
                    }
                    val expected = if (dialogs) "Bought 32 bread for 2 emeralds." else "Bought 16 bread for 1 emeralds."
                    val bread = onPlayer(player) { player.inventory.contents.filterNotNull().filter { it.type == Material.BREAD }.sumOf { it.amount } }
                    (said.fields[0] == expected && bread == (if (dialogs) 32 else 16)) to "said '${said.fields[0]}', holds $bread bread"
                }
                verdict("trader-offers-its-trades") {
                    bot.command("/trader")
                    bot.await("WINDOW") { it.fields[1] == "MERCHANT" && it.fields[2] == "Travelling trader" }
                    val offers = bot.await("TRADES").fields[1].split('|')
                    bot.closeWindow()
                    (offers.size == 2 && offers[0].endsWith("x4") && offers[1].endsWith("x1")) to "offers $offers"
                }
                verdict("staff-freezes-and-notes") {
                    bot.command("/staff")
                    bot.await("WINDOW") { it.fields[2] == "Online players" }
                    bot.awaitSlot(0, "SculkBot")
                    bot.click(0)
                    bot.await("WINDOW") { it.fields[2] == "Player actions" }
                    bot.click(10)
                    bot.await("CHAT") { it.fields[0] == "You are frozen. Wait for a member of staff." }
                    bot.click(10)
                    bot.await("CHAT") { it.fields[0] == "You can move again." }
                    bot.click(14)
                    bot.await("WINDOW") { it.fields[1] == "ANVIL" }
                    bot.rename("Helpful")
                    bot.awaitSlot(2, "Helpful")
                    bot.click(2)
                    bot.await("CHAT") { it.fields[0] == "Note saved." }
                    true to "frozen, freed and noted"
                }
                verdict("staff-removes-with-a-reason") {
                    bot.command("/staff")
                    bot.await("WINDOW") { it.fields[2] == "Online players" }
                    bot.awaitSlot(0, "SculkBot")
                    bot.click(0)
                    bot.await("WINDOW") { it.fields[2] == "Player actions" }
                    bot.click(16)
                    if (dialogs) {
                        bot.await("DIALOG") { it.fields[0] == "Remove a player" }
                        bot.answerDialog("Remove", "reason" to "Spam", "announce" to false)

                    } else {
                        bot.await("WINDOW") { it.fields[2] == "Remove for…" }
                        bot.awaitSlot(2, "Spam")
                        bot.click(2)
                    }
                    val gone = bot.await("DISCONNECTED")
                    ("Reason: Spam" in gone.fields[0]) to "disconnected with '${gone.fields[0]}'"
                }
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("hud-shows-on-join", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
    }

    // Folia has no scoreboards; Canvas has them back.
    private fun hasScoreboards(): Boolean {
        fun exists(name: String) = runCatching { Class.forName(name) }.isSuccess
        return !exists("io.papermc.paper.threadedregions.RegionizedServer") || exists("io.canvasmc.canvas.region.RegionTickData")
    }

    private fun <T> onPlayer(player: Player, action: () -> T): T {
        val result = CompletableFuture<T>()
        val scheduled = player.scheduler.run(this, { _ ->
            try {
                result.complete(action())
            } catch (failure: Throwable) {
                result.completeExceptionally(failure)
            }
        }, null)
        checkNotNull(scheduled) { "${player.name} is no longer in the world (valid ${player.isValid}, online ${player.isOnline})" }
        return runCatching { result.get(10, TimeUnit.SECONDS) }.getOrElse { failure ->
            throw IllegalStateException("a task on ${player.name}'s thread did not finish within 10s", failure)
        }
    }

    /**
     * Runs [command] as a sender that records its feedback. Executors start on the sender's
     * context, which for this console-like sender is the global region, already ours here, so the
     * replies arrive before dispatch returns; a short wait covers an executor that suspends.
     */
    private fun run(command: String): List<String> {
        val replies = CopyOnWriteArrayList<String>()
        val sender = server.createCommandSender { replies += PlainTextComponentSerializer.plainText().serialize(it.asComponent()) }
        try {
            server.dispatchCommand(sender, command)
        } catch (failure: CommandException) {
            replies += "threw ${failure.cause ?: failure}"
        }
        val deadline = System.nanoTime() + WAIT_NANOS
        while (replies.isEmpty() && System.nanoTime() < deadline) Thread.onSpinWait()
        return replies.toList()
    }

    private fun verdict(name: String, check: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = check()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    private fun write() {
        val file = File(dataFolder, "results.properties").also { it.parentFile.mkdirs() }
        file.writeText(results.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=${value.replace("\n", " | ")}" })
    }

    private companion object {
        const val WAIT_NANOS = 200_000_000L
    }
}
