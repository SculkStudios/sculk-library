@file:OptIn(SculkExperimental::class)

package studio.sculk.itest.npcs

import kotlinx.coroutines.future.asCompletableFuture
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.execution.getOrNull
import studio.sculk.getOrThrow
import studio.sculk.item.item
import studio.sculk.itest.bot.BotDriver
import studio.sculk.npc.Npc
import studio.sculk.npc.NpcSlot
import studio.sculk.npc.Npcs
import studio.sculk.npc.Skin
import studio.sculk.npc.npc
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.paper.teleportTo
import studio.sculk.paper.withEntity
import studio.sculk.paper.withRegion
import java.io.File
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * Spawns NPCs next to the test client and checks what it sees: the mannequin with its skin, name and
 * description, clicks handled on the player's thread, the head following the player, and removal
 * when the NPC's scope closes. On a server without mannequins it checks the failure instead. Writes
 * `results.properties` and stops the server.
 */
class NpcsCheckPlugin : SculkPaperPlugin(), Listener {
    private val results = sortedMapOf<String, String>()
    private val joined = CompletableFuture<Player>()
    private val rightClicks = LinkedBlockingQueue<String>()
    private val leftClicks = LinkedBlockingQueue<String>()

    // An unsigned property is enough for the client to report it back; it is never drawn.
    private val textures = Base64.getEncoder().encodeToString(
        """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/5c4ee1c1"}}}""".toByteArray(),
    )

    override suspend fun start() {
        val npcs = Npcs(sculk)
        server.pluginManager.registerEvents(this, this)
        server.globalRegionScheduler.runDelayed(this, { thread(name = "npcs-check") { check(npcs) } }, 20)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        joined.complete(event.player)
    }

    private fun check(npcs: Npcs) {
        try {
            BotDriver("SculkBot", server.port).use { bot ->
                bot.await("JOINED", 60)
                val player = joined.get(30, TimeUnit.SECONDS)
                if (npcs.isAvailable) checkNpcs(npcs, bot, player) else checkUnsupported(npcs, player)
            }
        } catch (failure: Throwable) {
            results.putIfAbsent("capability-matches-the-server", "FAIL: ${failure::class.simpleName}: ${failure.message}")
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.globalRegionScheduler.run(this) { server.shutdown() }
    }

    private fun checkUnsupported(npcs: Npcs, player: Player) {
        verdict("capability-matches-the-server") {
            val at = onEntity(player) { player.location }
            val result = onSculk { npcs.spawn(at, npc { name("Guide") }) }
            val failure = result as? SculkFailure.UnsupportedCapability
            // Nothing may be registered on a server where nothing can be spawned.
            val listening = PlayerInteractEntityEvent.getHandlerList().registeredListeners.any { it.plugin == this }
            (failure?.capability == "NPCs" && !npcs.isAvailable && npcs.spawned.isEmpty() && !listening) to
                "spawn gave $result, listening $listening"
        }
        SPAWNED.forEach { results[it] = "SKIP: no mannequins before Minecraft 1.21.9" }
    }

    private fun checkNpcs(npcs: Npcs, bot: BotDriver, player: Player) {
        val base = onEntity(player) { player.location }
        val scope = sculk.scope.resources.child("npcs-check")
        val guideAt = base.clone().add(3.0, 0.0, 0.0).apply { yaw = 90f }
        val guideSpec = npc {
            name("<gold>Guide <who>", "who" to "<red>Ada")
            description("Right-click me")
            skin = Skin.textures(textures)
            equip(NpcSlot.MAIN_HAND, item(Material.EMERALD))
            clickCooldown = 2.seconds
            onRightClick { click -> rightClicks.put("${click.button} owned=${Bukkit.isOwnedByCurrentRegion(click.player)}") }
            onLeftClick { click -> leftClicks.put("${click.button} owned=${Bukkit.isOwnedByCurrentRegion(click.player)}") }
        }
        lateinit var guide: Npc
        lateinit var guideId: String
        verdict("capability-matches-the-server") {
            guide = onSculk { npcs.spawn(guideAt, guideSpec, scope) }.getOrThrow()
            (npcs.isAvailable && guide.isSpawned) to "spawned $guide"
        }
        verdict("spawns-with-skin-and-name") {
            // The client reports only mannequins, so seeing it at all proves the entity type.
            guideId = bot.await("MANNEQUIN") { it.fields[1] == guide.id.toString() }.fields[0]
            bot.awaitState("MANNEQUINNAME", guideId) { it.fields[1] == "Guide <red>Ada" }
            bot.awaitState("MANNEQUINDESC", guideId) { it.fields[1] == "Right-click me" }
            bot.awaitState("MANNEQUINSKIN", guideId) { it.fields[1] == textures }
            val seen = onEntity(guide.entity) {
                val entity = guide.entity
                listOf(
                    entity.isPersistent,
                    entity.isInvulnerable,
                    entity.equipment?.itemInMainHand?.type == Material.EMERALD,
                    entity.world.getPluginChunkTickets(guideAt.blockX shr 4, guideAt.blockZ shr 4).contains(this),
                )
            }
            (seen == listOf(false, true, true, true)) to "persistent, invulnerable, emerald, ticket: $seen"
        }
        verdict("clicks-run-on-the-players-thread") {
            val id = guideId.toInt()
            bot.interact(id)
            val right = rightClicks.poll(10, TimeUnit.SECONDS)
            bot.interact(id)
            val again = rightClicks.poll(1, TimeUnit.SECONDS)
            bot.attack(id)
            val left = leftClicks.poll(10, TimeUnit.SECONDS)
            (right == "RIGHT owned=true" && again == null && left == "LEFT owned=true") to
                "right $right, again within the cooldown $again, left $left"
        }
        verdict("looks-at-the-nearest-player") {
            // South of the player and facing away from them; turning round means facing north.
            val watcherAt = base.clone().add(0.0, 0.0, 3.0).apply {
                yaw = 0f
                pitch = 0f
            }
            val watcher = onSculk {
                npcs.spawn(
                    watcherAt,
                    npc {
                        name("Watcher")
                        lookAtPlayers(range = 8.0)
                    },
                    scope,
                )
            }.getOrThrow()
            val added = bot.await("MANNEQUIN") { it.fields[1] == watcher.id.toString() }
            val id = added.fields[0]
            // It may already face the player when the client first sees it.
            if (!facing(added.fields[2].toFloat(), 180f)) bot.awaitState("MANNEQUINHEAD", id) { facing(it.fields[1].toFloat(), 180f) }
            onSculk { player.teleportTo(base.clone().add(0.0, 0.0, 6.0)) }
            bot.awaitState("MANNEQUINHEAD", id) { facing(it.fields[1].toFloat(), 0f) }
            onSculk { watcher.despawn() }
            bot.awaitState("MANNEQUINGONE", id) { true }
            val ticket = !sameChunk(watcherAt, guideAt) && !ticketReleased(watcherAt)
            (!watcher.isSpawned && !ticket) to "spawned ${watcher.isSpawned}, ticket kept $ticket"
        }
        verdict("despawns-when-its-scope-closes") {
            scope.close()
            bot.awaitState("MANNEQUINGONE", guideId) { true }
            val released = ticketReleased(guideAt)
            (!guide.isSpawned && npcs.spawned.isEmpty() && released) to
                "spawned ${guide.isSpawned}, left ${npcs.spawned}, released $released"
        }
    }

    // The ticket goes on the region's next tick after the mannequin, so give it a moment.
    private fun ticketReleased(at: Location): Boolean {
        val world = requireNotNull(at.world)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (onRegion(at) { this !in world.getPluginChunkTickets(at.blockX shr 4, at.blockZ shr 4) }) return true
        }
        return false
    }

    private fun sameChunk(a: Location, b: Location): Boolean = a.blockX shr 4 == b.blockX shr 4 && a.blockZ shr 4 == b.blockZ shr 4

    // Head yaw arrives as a byte angle, so allow for its rounding and for -180 meaning 180.
    private fun facing(yaw: Float, expected: Float): Boolean = abs(((yaw - expected) % 360f + 540f) % 360f - 180f) < 20f

    private fun <T> onSculk(block: suspend () -> T): T = sculk.scope.async { block() }.asCompletableFuture().get(30, TimeUnit.SECONDS)

    private fun <T : Any> onEntity(entity: Entity, block: () -> T): T =
        onSculk { withEntity(entity) { block() }.getOrNull() } ?: error("$entity was removed")

    private fun <T> onRegion(location: Location, block: () -> T): T = onSculk { withRegion(location) { block() } }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    private companion object {
        val SPAWNED = listOf(
            "spawns-with-skin-and-name",
            "clicks-run-on-the-players-thread",
            "looks-at-the-nearest-player",
            "despawns-when-its-scope-closes",
        )
    }
}
