package studio.sculk.scene

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.plugin.Plugin
import studio.sculk.SculkResult
import studio.sculk.execution.SculkScope
import studio.sculk.failureOrNull
import studio.sculk.placeholder.PlaceholderChange
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A chunk of one world, for scenes waiting for it to load. */
internal data class ChunkKey(val world: UUID, val x: Int, val z: Int)

/**
 * The Paper implementation of [Scenes]: owns the listener every scene shares and routes server
 * events and placeholder changes to the scenes they concern. Thread-safe; listeners run on
 * whichever region fires the event.
 */
internal class PaperScenes(val plugin: Plugin, parent: SculkScope, val placeholders: PlaceholderRegistry?, val text: Text) : Scenes {
    val scope: SculkScope = parent.child("scenes")
    private val scenes = ConcurrentHashMap.newKeySet<PaperScene>()
    private val owners = ConcurrentHashMap<UUID, PaperScene>()
    private val waiting = ConcurrentHashMap<ChunkKey, MutableSet<PaperScene>>()

    // Kept from join and quit events rather than asked of the server, whose player maps are not
    // meant to be read from region and async threads.
    private val online = ConcurrentHashMap<UUID, Player>()
    private val listener = SceneListener()
    private val counter = AtomicLong()

    init {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        plugin.server.onlinePlayers.forEach { online[it.uniqueId] = it }
        scope.resources.onClose { HandlerList.unregisterAll(listener) }
        placeholders?.onChange(::route)?.let { scope.resources.own(it) }
    }

    override val open: Collection<Scene> get() = scenes.toList()

    override fun spawn(spec: SceneSpec, anchor: Anchor, visibility: Visibility): SculkResult<Scene> {
        val viewers = Viewers.of(visibility)
        SceneRules.check(spec, viewers).failureOrNull()?.let { return it }
        val scene = PaperScene(this, spec, anchor, viewers, counter.incrementAndGet())
        scenes += scene
        scene.start()
        return SculkResult.success(scene)
    }

    fun player(id: UUID): Player? = online[id]

    fun own(entity: UUID, scene: PaperScene) {
        owners[entity] = scene
    }

    fun disown(entity: UUID) {
        owners.remove(entity)
    }

    fun await(chunk: ChunkKey, scene: PaperScene) {
        waiting.compute(chunk) { _, waiters -> (waiters ?: ConcurrentHashMap.newKeySet()).apply { add(scene) } }
    }

    fun stopAwaiting(chunk: ChunkKey, scene: PaperScene) {
        waiting.computeIfPresent(chunk) { _, waiters -> waiters.apply { remove(scene) }.takeIf { it.isNotEmpty() } }
    }

    fun ended(scene: PaperScene) {
        scenes -= scene
        waiting.keys.forEach { stopAwaiting(it, scene) }
    }

    private fun route(change: PlaceholderChange) {
        val tag = change.key.tag
        scenes.forEach { it.signal(change, tag) }
    }

    private inner class SceneListener : Listener {
        // Fires for every removal, unloads included, on the thread that removes the entity.
        @EventHandler(priority = EventPriority.MONITOR)
        fun onRemove(event: EntityRemoveEvent) {
            val id = event.entity.uniqueId
            owners.remove(id)?.removed(id, unloaded = event.cause == EntityRemoveEvent.Cause.UNLOAD)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onChunkLoad(event: ChunkLoadEvent) {
            waiting[ChunkKey(event.world.uid, event.chunk.x, event.chunk.z)]?.forEach { it.chunkLoaded() }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onJoin(event: PlayerJoinEvent) {
            online[event.player.uniqueId] = event.player
            scenes.forEach { it.joined(event.player.uniqueId) }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onQuit(event: PlayerQuitEvent) {
            online.remove(event.player.uniqueId)
            scenes.forEach { it.left(event.player.uniqueId) }
        }
    }
}
