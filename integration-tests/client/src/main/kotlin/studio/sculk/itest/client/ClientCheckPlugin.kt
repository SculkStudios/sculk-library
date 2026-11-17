package studio.sculk.itest.client

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.itest.bot.BotDriver
import studio.sculk.itest.bot.slots
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Joins with the test client, opens a chest for it, has it click a named item and checks the click
 * reached the server on the player's own thread. Writes `results.properties` and stops the server.
 */
class ClientCheckPlugin : JavaPlugin(), Listener {
    private val results = sortedMapOf<String, String>()
    private val clicked = CompletableFuture<String>()

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "client-check") { check() } }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        // The player's own scheduler: its region thread on Folia and Canvas, the main thread on Paper.
        player.scheduler.runDelayed(this, {
            val inventory = Bukkit.createInventory(null, 27, Component.text("Sculk Check"))
            inventory.setItem(4, ItemStack.of(Material.EMERALD).apply { setData(DataComponentTypes.ITEM_NAME, Component.text("Press me")) })
            player.openInventory(inventory)
        }, null, 5)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val owned = Bukkit.isOwnedByCurrentRegion(event.whoClicked)
        clicked.complete("slot ${event.rawSlot} ${event.click} owned $owned")
    }

    private fun check() {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                verdict("client-joins") { bot.await("JOINED", 60).let { true to "joined" } }
                verdict("window-shows-the-item") {
                    val window = bot.await("WINDOW")
                    val content = bot.await("CONTENT")
                    (window.fields[2] == "Sculk Check" && content.slots()[4] == "Press me") to "window $window, content $content"
                }
                verdict("click-reaches-the-server") {
                    bot.click(4)
                    val seen = clicked.get(15, TimeUnit.SECONDS)
                    (seen == "slot 4 LEFT owned true") to "server saw '$seen'"
                }
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("client-joins", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
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
