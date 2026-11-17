package studio.sculk.itest.menus

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.ResourceScope
import studio.sculk.inventory.MenuLayout
import studio.sculk.inventory.MenuSession
import studio.sculk.inventory.Menus
import studio.sculk.inventory.menu
import studio.sculk.item.item
import studio.sculk.itest.bot.BotDriver
import studio.sculk.itest.bot.slots
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Opens a counter menu for the test client and has it click, shift-click and swap into it, page it,
 * update it from another thread and close it. Writes `results.properties` and stops the server.
 */
class MenusCheckPlugin : JavaPlugin(), Listener {
    data class Counter(val count: Int = 0, val page: Int = 1)

    private val results = sortedMapOf<String, String>()
    private val plain = PlainTextComponentSerializer.plainText()
    private val scope = ResourceScope.root("menus-check")
    private lateinit var menus: Menus
    private val joined = CompletableFuture<Player>()
    private val clickedOnOwnThread = CompletableFuture<Boolean>()
    private val closed = CompletableFuture<String>()

    private val counter = menu<Counter>(MenuLayout.chest(3)) {
        title { state -> "Page ${state.page}" }
        render { state ->
            slot(11, item(Material.LIME_DYE) { name = "Add" }) {
                clickedOnOwnThread.complete(Bukkit.isOwnedByCurrentRegion(player))
                update { it.copy(count = it.count + 1) }
            }
            slot(13, item(Material.PAPER) { name = "Count ${state.count}" })
            slot(26, item(Material.ARROW) { name = "Next" }) { update { it.copy(page = it.page + 1) } }
            fill(item(Material.GRAY_STAINED_GLASS_PANE) { name = "Pane" })
        }
        onClose { state, player -> closed.complete("count ${state.count} owned ${Bukkit.isOwnedByCurrentRegion(player)}") }
    }

    override fun onEnable() {
        menus = Menus(this, scope)
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "menus-check") { check() } }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        event.player.inventory.setItem(0, item(Material.DIAMOND) { name = "Loot" }.create())
        joined.complete(event.player)
    }

    private fun check() {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                bot.await("JOINED", 60)
                val player = joined.get(30, TimeUnit.SECONDS)
                lateinit var session: MenuSession<Counter>
                verdict("menu-shows") {
                    session = menus.open(player, counter, Counter())
                    val window = bot.await("WINDOW")
                    val slots = bot.await("CONTENT").slots()
                    (window.fields[2] == "Page 1" && slots[11] == "Add" && slots[13] == "Count 0" && slots[0] == "Pane") to "window $window, slots $slots"
                }
                verdict("button-runs-on-the-players-thread") {
                    bot.click(11)
                    val owned = clickedOnOwnThread.get(15, TimeUnit.SECONDS)
                    val shown = bot.awaitSlot(13, "Count 1")
                    (owned && session.state.count == 1) to "owned $owned, state ${session.state}, shown $shown"
                }
                verdict("player-items-never-enter") {
                    bot.click(54, "SHIFT") // raw slot of the hotbar's first slot: after 27 menu and 27 storage slots
                    bot.click(4, "HOTBAR", 0)
                    Thread.sleep(1_500)
                    val menuSlot = onPlayerThread(player) { player.openInventory.topInventory.getItem(4)?.getData(DataComponentTypes.ITEM_NAME)?.let(plain::serialize) }
                    val loot = onPlayerThread(player) { player.inventory.getItem(0)?.getData(DataComponentTypes.ITEM_NAME)?.let(plain::serialize) }
                    (menuSlot == "Pane" && loot == "Loot") to "menu slot 4 '$menuSlot', hotbar 0 '$loot'"
                }
                verdict("update-from-another-thread-hops-to-the-player") {
                    session.update { it.copy(count = it.count + 10) }
                    val shown = bot.awaitSlot(13, "Count 11")
                    true to "shown $shown"
                }
                verdict("title-change-reopens") {
                    bot.click(26)
                    val window = bot.await("WINDOW") { it.fields[2] == "Page 2" }
                    val slots = bot.await("CONTENT").slots()
                    (slots[13] == "Count 11" && session.isOpen) to "window $window, slots $slots, open ${session.isOpen}"
                }
                verdict("closing-runs-on-close") {
                    bot.closeWindow()
                    val seen = closed.get(15, TimeUnit.SECONDS)
                    (seen == "count 11 owned true" && !session.isOpen && menus.open.isEmpty()) to "on close saw '$seen', open ${session.isOpen}"
                }
                verdict("scope-close-closes-menus") {
                    menus.open(player, counter, Counter())
                    bot.await("WINDOW")
                    scope.close()
                    bot.await("CLOSED")
                    true to "closed"
                }
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("menu-shows", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
    }

    private fun <T> onPlayerThread(player: Player, read: () -> T): T {
        val result = CompletableFuture<T>()
        player.scheduler.run(this, { result.complete(read()) }, null)
        return result.get(10, TimeUnit.SECONDS)
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
