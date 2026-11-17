package studio.sculk.npc

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin
import studio.sculk.ResourceScope
import studio.sculk.SculkFailure
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.capability.Capability
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.WorldKey
import studio.sculk.paper.PaperCapabilities
import studio.sculk.paper.PaperRuntime
import studio.sculk.paper.ref
import studio.sculk.paper.withEntity
import studio.sculk.paper.withRegion
import studio.sculk.runtime.SculkLog
import studio.sculk.runtime.SculkRuntime
import studio.sculk.text.Text
import studio.sculk.time.SculkClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Spawns [NpcSpec]s as Paper mannequins (ADR-0033). One per plugin; every NPC it spawned is removed
 * when the runtime's scope closes, and each also when the scope it was spawned into closes.
 *
 * Mannequins exist from Minecraft 1.21.9: [isAvailable] says whether this server has them, and
 * [spawn] returns [SculkFailure.UnsupportedCapability] where it does not. On such a server nothing
 * is registered and nothing runs.
 *
 * Thread-safe. An NPC lives on its region's thread: spawning runs on the region owning the
 * location, head turning on the NPC's own thread, and click handlers on the clicking player's.
 */
@SculkExperimental
public class Npcs internal constructor(
    private val runtime: SculkRuntime,
    internal val plugin: Plugin,
    private val text: Text,
    adapters: () -> MannequinAdapter,
) {
    /** NPCs for the plugin [runtime] belongs to; names and descriptions are rendered with [text]. */
    public constructor(runtime: PaperRuntime, text: Text = Text()) : this(runtime, runtime.plugin, text, MannequinAdapters::load)

    private val npcs = ConcurrentHashMap<UUID, Npc>()
    private val tickets = ChunkTickets()
    private val listener = NpcListener()
    private val listening = AtomicBoolean()
    private val adapter: MannequinAdapter by lazy(adapters)

    @Volatile private var closed = false

    internal val executor: SculkExecutor get() = runtime.scope.execution.executor
    internal val clock: SculkClock get() = runtime.clock
    internal val log: SculkLog get() = runtime.log

    private val skins = SkinCache(runtime.scope, runtime.scope.execution.dispatcher(ExecutionContext.Async), ::lookUpSkin)

    init {
        runtime.scope.resources.onClose(::shutDown)
    }

    /** Whether this server has mannequins: Paper, Folia and Canvas from Minecraft 1.21.9. */
    public val isAvailable: Boolean get() = runtime.capabilities[PaperCapabilities.MANNEQUINS].isAvailable

    /** The NPCs spawned and not yet gone, for diagnostics. */
    public val spawned: Collection<Npc> get() = npcs.values.toList()

    /** The NPC whose mannequin is [entity], or null when it is not one of this `Npcs`'s. */
    public fun npc(entity: Entity): Npc? = npcs[entity.uniqueId]

    /**
     * Spawns [spec] at [location] and returns it once it stands in the world. Call from a coroutine
     * in a Sculk scope, on any thread: the chunk is loaded without blocking a tick and the mannequin
     * is spawned on the region that owns it, and the caller resumes in its own context.
     *
     * The NPC belongs to [scope] and goes when it closes, or when this `Npcs`'s runtime does. Its
     * chunk stays loaded while it lives, and the mannequin is never saved with the world.
     *
     * Returns [SculkFailure.UnsupportedCapability] on a server without mannequins and
     * [SculkFailure.InvalidInput] for equipment the server cannot build or a chunk that unloaded
     * again before the mannequin could stand in it. Throws
     * [IllegalArgumentException] for a location without a world and [IllegalStateException] when
     * [scope] or the runtime is already closed.
     */
    public suspend fun spawn(location: Location, spec: NpcSpec, scope: ResourceScope = runtime.scope.resources): SculkResult<Npc> {
        unsupported()?.let { return it }
        val world = requireNotNull(location.world) { "An NPC needs a location with a world: $location" }
        check(!closed && !scope.isClosed) { "Cannot spawn an NPC into closed scope '${scope.name}'." }
        spec.equipment.values.forEach { item -> (item.check() as? SculkFailure)?.let { return it } }
        listen()
        val at = location.clone()
        val chunkX = at.blockX shr CHUNK_SHIFT
        val chunkZ = at.blockZ shr CHUNK_SHIFT
        world.getChunkAtAsync(chunkX, chunkZ).await()
        val known = skins.known(spec.skin)
        return withRegion(at) {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return@withRegion SculkFailure.InvalidInput(
                    "NPC location",
                    "a chunk that stays loaded",
                    "chunk $chunkX, $chunkZ of ${world.name} unloaded again before the NPC could stand in it",
                )
            }
            val entity = adapter.spawn(at) { mannequin -> setUp(mannequin, spec, known) }
            val chunk = ChunkKey(world.uid, chunkX, chunkZ)
            if (tickets.acquire(chunk)) world.addPluginChunkTicket(chunkX, chunkZ, plugin)
            val npc = Npc(this@Npcs, spec, entity, chunk)
            npcs[npc.id] = npc
            npc.start(scope, ::lookEvery) { started -> if (known == null) resolveSkin(started) else null }
            SculkResult.success(npc)
        }
    }

    internal fun owns(player: Player): Boolean = executor.owns(ExecutionContext.Entity(player.ref))

    /** Runs [action] on [player]'s thread: at once when the caller owns the player, else on their next tick. */
    internal fun onPlayer(player: Player, action: () -> Unit) {
        val context = ExecutionContext.Entity(player.ref)
        if (executor.owns(context)) {
            action()
            return
        }
        schedule(
            context,
            object : SculkExecutor.Work {
                override fun run() = action()
            },
        )
    }

    /** Schedules [work] for the next tick; a refusal means the server or plugin is shutting down, and the work is dropped. */
    internal fun schedule(context: ExecutionContext, work: SculkExecutor.Work) {
        try {
            executor.schedule(context, 0, work)
        } catch (_: IllegalStateException) {
            // Shutting down: nothing scheduled now would run.
        }
    }

    internal fun ended(npc: Npc) {
        npcs.remove(npc.id, npc)
    }

    /**
     * Gives up [chunk]'s ticket once no NPC stands in it. With [later] the removal always waits for the
     * region's next tick: retired entity tasks and entity-removal events run where Paper forbids touching the world.
     */
    internal fun releaseChunk(chunk: ChunkKey, later: Boolean) {
        if (!tickets.release(chunk)) return
        val world = Bukkit.getWorld(chunk.world) ?: return
        val context = ExecutionContext.Region(WorldKey(world.key.asString()), chunk.x, chunk.z)
        if (!later && executor.owns(context)) {
            world.removePluginChunkTicket(chunk.x, chunk.z, plugin)
            return
        }
        schedule(
            context,
            object : SculkExecutor.Work {
                override fun run() {
                    // Another NPC may have taken the chunk again while this waited.
                    if (tickets.count(chunk) == 0) world.removePluginChunkTicket(chunk.x, chunk.z, plugin)
                }
            },
        )
    }

    private fun unsupported(): SculkFailure? {
        val capability = runtime.capabilities[PaperCapabilities.MANNEQUINS]
        if (capability !is Capability.Unavailable) return null
        return SculkFailure.UnsupportedCapability(
            "NPCs",
            capability.reason,
            hint = "Sculk's NPCs are Mannequin entities, which Paper has from Minecraft 1.21.9; check Npcs.isAvailable before spawning.",
        )
    }

    // Region thread, before the mannequin enters the world.
    private fun setUp(entity: LivingEntity, spec: NpcSpec, texture: SkinTexture?) {
        entity.isPersistent = false
        entity.isInvulnerable = spec.invulnerable
        entity.isSilent = spec.silent
        entity.isCollidable = spec.collidable
        spec.name?.let { name ->
            entity.customName(name.render(text))
            entity.isCustomNameVisible = true
        }
        entity.setPose(spec.pose.bukkit(), true)
        entity.equipment?.let { equipment ->
            spec.equipment.forEach { (slot, item) -> equipment.setItem(slot.bukkit(), item.create(text)) }
        }
        adapter.dress(entity, spec.mainHand, spec.immovable, spec.description?.render(text), spec.showDescription)
        if (texture != null) adapter.skin(entity, texture.value, texture.signature)
    }

    private fun lookEvery(npc: Npc, look: () -> Unit): SculkHandle {
        val ticks = npc.spec.lookAt?.everyTicks ?: return SculkHandle.NONE
        return try {
            executor.scheduleRepeating(
                ExecutionContext.Entity(npc.entity.ref),
                ticks,
                ticks,
                object : SculkExecutor.Work {
                    override fun run() = look()
                },
            )
        } catch (_: IllegalStateException) {
            SculkHandle.NONE
        }
    }

    private fun resolveSkin(npc: Npc): Job? {
        val skin = npc.spec.skin as? Skin.OfPlayer ?: return null
        return runtime.scope.launch(runtime.scope.execution.dispatcher(ExecutionContext.Async)) {
            val texture = skins.resolve(skin.uuid) ?: return@launch
            withEntity(npc.entity) { if (npc.isSpawned) adapter.skin(npc.entity, texture.value, texture.signature) }
        }
    }

    // Async context: a profile lookup may wait on Mojang, which is exactly what this context is for.
    private suspend fun lookUpSkin(uuid: UUID): SkinTexture? = try {
        val profile = Bukkit.createProfile(uuid)
        if (!profile.complete(true, true)) {
            log.warn("No skin was found for player $uuid; their NPCs keep the default skin.")
            null
        } else {
            profile.properties.firstOrNull { it.name == "textures" }?.let { SkinTexture(it.value, it.signature) }
        }
    } catch (failure: Exception) {
        log.warn("Looking up the skin of player $uuid failed; their NPCs keep the default skin.", failure)
        null
    }

    private fun listen() {
        if (listening.compareAndSet(false, true)) plugin.server.pluginManager.registerEvents(listener, plugin)
    }

    private fun shutDown() {
        closed = true
        npcs.values.toList().forEach(Npc::close)
        if (listening.get()) HandlerList.unregisterAll(listener)
    }

    private inner class NpcListener : Listener {
        // Clicks count even when another plugin, such as a spawn protection, cancelled the event first.
        @EventHandler(priority = EventPriority.LOW)
        fun onInteract(event: PlayerInteractEntityEvent) {
            val npc = npcs[event.rightClicked.uniqueId] ?: return
            event.isCancelled = true
            if (event.hand == EquipmentSlot.HAND) npc.clicked(event.player, NpcClick.Button.RIGHT)
        }

        @EventHandler(priority = EventPriority.LOW)
        fun onAttack(event: PrePlayerAttackEntityEvent) {
            val npc = npcs[event.attacked.uniqueId] ?: return
            event.isCancelled = true
            npc.clicked(event.player, NpcClick.Button.LEFT)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onRemove(event: EntityRemoveFromWorldEvent) {
            npcs[event.entity.uniqueId]?.removedByServer()
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onQuit(event: PlayerQuitEvent) {
            val player = event.player.uniqueId
            npcs.values.forEach { it.forget(player) }
        }
    }

    private companion object {
        const val CHUNK_SHIFT = 4
    }
}

internal fun NpcPose.bukkit(): Pose = when (this) {
    NpcPose.STANDING -> Pose.STANDING
    NpcPose.SNEAKING -> Pose.SNEAKING
    NpcPose.SWIMMING -> Pose.SWIMMING
    NpcPose.GLIDING -> Pose.FALL_FLYING
    NpcPose.SLEEPING -> Pose.SLEEPING
}

internal fun NpcSlot.bukkit(): EquipmentSlot = when (this) {
    NpcSlot.MAIN_HAND -> EquipmentSlot.HAND
    NpcSlot.OFF_HAND -> EquipmentSlot.OFF_HAND
    NpcSlot.HEAD -> EquipmentSlot.HEAD
    NpcSlot.CHEST -> EquipmentSlot.CHEST
    NpcSlot.LEGS -> EquipmentSlot.LEGS
    NpcSlot.FEET -> EquipmentSlot.FEET
}
