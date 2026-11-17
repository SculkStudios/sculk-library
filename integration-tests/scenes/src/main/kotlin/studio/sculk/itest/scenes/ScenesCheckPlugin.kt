@file:OptIn(SculkExperimental::class)

package studio.sculk.itest.scenes

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import studio.sculk.annotation.SculkExperimental
import studio.sculk.execution.SculkScope
import studio.sculk.getOrThrow
import studio.sculk.item.item
import studio.sculk.itest.bot.BotDriver
import studio.sculk.itest.bot.BotEvent
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.placeholder.Refresh
import studio.sculk.scene.Anchor
import studio.sculk.scene.Scenes
import studio.sculk.scene.Transform
import studio.sculk.scene.Vec3
import studio.sculk.scene.Visibility
import studio.sculk.scene.scene
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Shows scenes to two test clients and checks the display entities each one receives: every kind
 * spawned, an update that sends only what changed, chosen viewers, per-viewer text, a scene waiting
 * for its chunk and coming back after an unload, following a player across a far teleport, and
 * closing the scope. Writes `results.properties` and stops the server.
 */
class ScenesCheckPlugin : SculkPaperPlugin(), Listener {
    private val results = sortedMapOf<String, String>()
    private val joined = ConcurrentHashMap<String, CompletableFuture<Player>>()

    @Volatile private var coins = 10

    override suspend fun start() {
        val placeholders = PlaceholderRegistry(sculk.scope)
        val coinsTag = placeholders.global("itest:coins", Refresh.UntilInvalidated) { coins }
        placeholders.audience("itest:name", Refresh.Live) { (audience as? Player)?.name }
        val scope = sculk.scope.child("scenes-check")
        val scenes = Scenes(this, scope, placeholders)
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "scenes-check") { check(scenes, scope, coinsTag::invalidate) } }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        future(event.player.name).complete(event.player)
    }

    private fun future(name: String): CompletableFuture<Player> = joined.computeIfAbsent(name) { CompletableFuture() }

    private fun check(scenes: Scenes, scope: SculkScope, changed: () -> Unit) {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                bot.await("JOINED", 60)
                val player = future("SculkBot").get(30, TimeUnit.SECONDS)
                BotDriver("OtherBot", server.port).use { other ->
                    other.await("JOINED", 60)
                    val otherPlayer = future("OtherBot").get(30, TimeUnit.SECONDS)
                    val home = onPlayer(player) { player.location.clone() }
                    teleport(otherPlayer, home)
                    checks(scenes, scope, changed, bot, other, player, otherPlayer, home)
                }
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("a-scene-spawns-every-kind-of-display", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
    }

    private fun checks(
        scenes: Scenes,
        scope: SculkScope,
        changed: () -> Unit,
        bot: BotDriver,
        other: BotDriver,
        player: Player,
        otherPlayer: Player,
        home: Location,
    ) {
        val at = home.clone().add(0.0, 2.0, 3.0)
        val spec = scene {
            text("coins", "Coins <itest_coins>")
            item("icon", item(Material.DIAMOND)) { transform = Transform(translation = Vec3(1f, 0f, 0f)) }
            block("base", server.createBlockData("minecraft:stone")) { transform = Transform(translation = Vec3(-1f, 0f, 0f)) }
        }
        val board = scenes.spawn(spec, Anchor.at(at)).getOrThrow()
        val ids = HashMap<String, String>()

        verdict("a-scene-spawns-every-kind-of-display") {
            repeat(3) {
                val added = bot.await("DISPLAY")
                ids[added.fields[1]] = added.fields[0]
            }
            val coinsId = ids.getValue("TEXT_DISPLAY")
            bot.awaitState("DISPLAYDATA", coinsId) { text(it) == "Coins 10" }
            bot.awaitState("DISPLAYDATA", ids.getValue("ITEM_DISPLAY")) { event -> values(event).any { it.startsWith("item:") } }
            bot.awaitState("DISPLAYDATA", ids.getValue("BLOCK_DISPLAY")) { event -> values(event).any { it.startsWith("block:") } }
            board.isSpawned to "spawned ${ids.keys}, isSpawned ${board.isSpawned}"
        }

        verdict("an-update-sends-only-what-changed") {
            val coinsId = ids.getValue("TEXT_DISPLAY")
            val icon = ids.getValue("ITEM_DISPLAY")
            board.update(
                spec.with {
                    item("icon", item(Material.DIAMOND)) {
                        transform = Transform(translation = Vec3(1f, 1.5f, 0f))
                        interpolationTicks = 10
                    }
                },
            ).getOrThrow()
            val moved = bot.await("DISPLAYDATA") { it.fields[0] == icon && "vec:1.0,1.5,0.0" in values(it) }
            val textTouched = bot.sees("DISPLAYDATA", 2_000) { it.fields[0] == coinsId }
            coins = 11
            changed()
            bot.await("DISPLAYDATA") { it.fields[0] == coinsId && text(it) == "Coins 11" }
            val iconTouched = bot.sees("DISPLAYDATA", 2_000) { it.fields[0] == icon }
            ("int:10" in values(moved) && !textTouched && !iconTouched) to
                "interpolation in ${values(moved)}, text resent on a move: $textTouched, icon resent on a text change: $iconTouched"
        }

        verdict("only-chosen-players-see-it") {
            val secretSpec = scene { text("secret", "Only for you") }
            val secret = scenes.spawn(secretSpec, Anchor.at(at.clone().add(0.0, 0.0, 2.0)), Visibility.only(player)).getOrThrow()
            val mine = bot.await("DISPLAYDATA") { text(it) == "Only for you" }.fields[0]
            val otherSaw = other.sees("DISPLAYDATA", 3_000) { text(it) == "Only for you" }
            secret.show(otherPlayer)
            other.await("DISPLAYDATA") { text(it) == "Only for you" }
            secret.hide(player)
            bot.await("DISPLAYGONE") { it.fields[0] == mine }
            secret.close()
            (!otherSaw) to "the other client saw it before it was shown to them"
        }

        verdict("per-viewer-text-reaches-each-viewer-alone") {
            val hello = scenes.spawn(
                scene { text("hello", "Hello <itest_name>", perViewer = true) },
                Anchor.at(at.clone().add(0.0, 0.0, 4.0)),
                Visibility.only(player, otherPlayer),
            ).getOrThrow()
            val botSaw = greetings(bot)
            val otherSaw = greetings(other)
            hello.close()
            (botSaw == setOf("Hello SculkBot") && otherSaw == setOf("Hello OtherBot")) to "SculkBot saw $botSaw, OtherBot saw $otherSaw"
        }

        val distant = home.clone().add(0.0, 0.0, 3000.0).apply { y = 300.0 }
        val far = scenes.spawn(scene { text("far", "Far away") }, Anchor.at(distant)).getOrThrow()

        verdict("it-waits-for-its-chunk-to-load") {
            Thread.sleep(2_000)
            val early = far.isSpawned
            teleport(player, distant.clone().add(0.0, 0.0, -3.0))
            bot.await("DISPLAYDATA") { text(it) == "Far away" }
            (!early) to "it spawned before its chunk was loaded"
        }

        verdict("it-comes-back-after-its-chunk-unloads") {
            teleport(player, home)
            // Paper decides when an unwatched chunk unloads; a server that keeps it says nothing about scenes.
            if (!waitFor(60_000) { !far.isSpawned }) throw Skip("the server kept the chunk loaded for 60 seconds")
            teleport(player, distant.clone().add(0.0, 0.0, -3.0))
            bot.await("DISPLAYDATA") { text(it) == "Far away" }
            waitFor(5_000) { far.isSpawned } to "it did not report itself spawned again"
        }
        far.close()

        var badgeId: String? = null
        val away = home.clone().add(2_000.0, 0.0, 0.0)

        verdict("it-follows-across-a-far-teleport") {
            teleport(player, home)
            val badge = scenes.spawn(
                scene {
                    teleportTicks = 2
                    text("badge", "Following")
                },
                Anchor.following(player, Vec3(0f, 2.5f, 0f)),
                Visibility.only(player),
            ).getOrThrow()
            val first = bot.await("DISPLAYDATA") { text(it) == "Following" }.fields[0]
            val start = bot.awaitState("DISPLAY", first) { true }
            teleport(player, away)
            val second = bot.await("DISPLAYDATA") { text(it) == "Following" && it.fields[0] != first }.fields[0]
            badgeId = second
            val arrived = bot.awaitState("DISPLAY", second) { true }
            (near(start, home) && near(arrived, away) && badge.isOpen) to "started at ${start.fields}, arrived at ${arrived.fields}"
        }

        verdict("closing-the-scope-removes-everything") {
            scope.close()
            badgeId?.let { id -> bot.awaitState("DISPLAYGONE", id) { true } }
            val homeClear = waitFor(5_000) { displaysNear(home) == 0 }
            val awayClear = waitFor(5_000) { displaysNear(away) == 0 }
            (homeClear && awayClear && scenes.open.isEmpty()) to
                "displays left at home: ${displaysNear(home)}, where the badge was: ${displaysNear(away)}, open scenes: ${scenes.open.size}"
        }
    }

    // Collects every greeting a client receives for four seconds, so a leak cannot hide behind ordering.
    private fun greetings(bot: BotDriver): Set<String> {
        val seen = HashSet<String>()
        bot.sees("DISPLAYDATA", 4_000) { event ->
            text(event)?.takeIf { it.startsWith("Hello") }?.let(seen::add)
            false
        }
        return seen
    }

    // A DISPLAY event's x and z within two blocks of [to]'s.
    private fun near(added: BotEvent, to: Location): Boolean =
        abs(added.fields[2].toDouble() - to.x) < 2 && abs(added.fields[4].toDouble() - to.z) < 2

    private fun displaysNear(location: Location): Int =
        onRegion(location) { location.world.getNearbyEntitiesByType(Display::class.java, location, 32.0).size }

    private fun text(event: BotEvent): String? = values(event).firstOrNull { it.startsWith("text:") }?.removePrefix("text:")

    private fun values(event: BotEvent): List<String> = event.fields.drop(1).map { it.substringAfter('=') }

    private fun teleport(player: Player, to: Location) {
        onPlayer(player) {
            player.allowFlight = true
            player.teleportAsync(to)
        }.get(30, TimeUnit.SECONDS)
    }

    private fun waitFor(millis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(250)
        }
        return condition()
    }

    private fun <T> onPlayer(player: Player, action: () -> T): T {
        val result = CompletableFuture<T>()
        player.scheduler.run(this, { result.complete(action()) }, null)
        return result.get(10, TimeUnit.SECONDS)
    }

    private fun <T> onRegion(location: Location, action: () -> T): T {
        val result = CompletableFuture<T>()
        Bukkit.getRegionScheduler().run(this, location) { result.complete(action()) }
        return result.get(10, TimeUnit.SECONDS)
    }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (skip: Skip) {
            "SKIP: ${skip.message}"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    /** A check that could not be made on this server, which is not a failure of the code under test. */
    private class Skip(message: String) : Exception(message)
}
