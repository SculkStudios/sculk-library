package studio.sculk.npc

import io.papermc.paper.entity.LookAnchor
import kotlinx.coroutines.Job
import org.bukkit.GameMode
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import studio.sculk.ResourceScope
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkExperimental
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.paper.ref
import studio.sculk.paper.withEntity
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A spawned NPC. It stays until [despawn] or [close], until the scope it was spawned into closes, or
 * until the server removes its entity, whichever comes first; all of them are safe to repeat.
 */
@SculkExperimental
public class Npc internal constructor(
    private val npcs: Npcs,
    public val spec: NpcSpec,
    /**
     * The mannequin, for what the spec does not cover. Touch it only on its own thread, inside
     * `withEntity(npc.entity)`. Never teleport or remove it directly: despawn and spawn again to move
     * an NPC, or its chunk is kept loaded in the wrong place.
     */
    public val entity: LivingEntity,
    internal val chunk: ChunkKey,
) : SculkHandle {
    /** The entity's UUID; [Npcs.npc] finds the NPC by it. */
    public val id: UUID = entity.uniqueId

    private val live = AtomicBoolean(true)
    private val leftClicks = ClickGate(spec.clickCooldown.inWholeNanoseconds)
    private val rightClicks = ClickGate(spec.clickCooldown.inWholeNanoseconds)
    private val homeYaw = entity.location.yaw
    private val homePitch = entity.location.pitch

    // Set once while spawning; volatile because a closing scope may end the NPC from another thread meanwhile.
    @Volatile private var scope: ResourceScope? = null

    @Volatile private var looking: SculkHandle = SculkHandle.NONE

    @Volatile private var skin: Job? = null

    // Entity thread only: whether the head currently follows a player.
    private var following = false

    /** True until the NPC is despawned, closed or removed by the server. */
    public val isSpawned: Boolean get() = live.get()

    /**
     * Removes the NPC on its own thread and suspends until it is gone. Call from a coroutine in a
     * Sculk scope; does nothing for an NPC already gone.
     */
    public suspend fun despawn() {
        if (!end()) return
        withEntity(entity) { entity.remove() }
        npcs.releaseChunk(chunk, later = false)
    }

    /**
     * Removes the NPC from any thread: at once when the caller owns it, otherwise on its next tick.
     * During shutdown, when no task can run any more, the entity is left to the server, which never
     * saves it (ADR-0033).
     */
    override fun close() {
        if (!end()) return
        val context = ExecutionContext.Entity(entity.ref)
        if (npcs.executor.owns(context)) {
            entity.remove()
            npcs.releaseChunk(chunk, later = false)
            return
        }
        npcs.schedule(
            context,
            object : SculkExecutor.Work {
                override fun run() {
                    entity.remove()
                    npcs.releaseChunk(chunk, later = false)
                }

                override fun retired() = npcs.releaseChunk(chunk, later = true)
            },
        )
    }

    override fun toString(): String = "Npc(${spec.name?.template ?: id}, spawned=$isSpawned)"

    internal fun start(owner: ResourceScope, look: (Npc, () -> Unit) -> SculkHandle, resolveSkin: (Npc) -> Job?) {
        if (spec.lookAt != null) looking = look(this, ::lookAtNearest)
        skin = resolveSkin(this)
        scope = owner
        owner.own(this)
        // A scope closing while this ran may have ended the NPC before the handles above existed.
        if (!isSpawned) {
            looking.close()
            skin?.cancel()
        }
    }

    /** The server removed the entity without Sculk asking; runs on the entity's thread. */
    internal fun removedByServer() {
        if (end()) npcs.releaseChunk(chunk, later = true)
    }

    internal fun clicked(player: Player, button: NpcClick.Button) {
        val handler = (if (button == NpcClick.Button.LEFT) spec.onLeftClick else spec.onRightClick) ?: return
        val gate = if (button == NpcClick.Button.LEFT) leftClicks else rightClicks
        if (!isSpawned || !gate.pass(player.uniqueId, npcs.clock.monotonicNanos())) return
        val click = NpcClick(player, this, button, player.isSneaking)
        npcs.onPlayer(player) {
            try {
                handler(click)
            } catch (failure: Exception) {
                npcs.log.error("An NPC's $button-click handler failed for ${player.name}.", failure)
            }
        }
    }

    internal fun forget(player: UUID) {
        leftClicks.forget(player)
        rightClicks.forget(player)
    }

    /** Marks the NPC gone exactly once and stops everything that serves it; false when it already was. */
    private fun end(): Boolean {
        if (!live.compareAndSet(true, false)) return false
        looking.close()
        skin?.cancel()
        npcs.ended(this)
        scope?.release(this)
        return true
    }

    // Entity thread. Only players this thread owns are read: on Folia and Canvas a player in another
    // region is another thread's state, and a tracked player that close is almost always in this region.
    private fun lookAtNearest() {
        if (!isSpawned) return
        val range = spec.lookAt?.range ?: return
        val eye = entity.eyeLocation
        val target = nearestWithin(range, entity.trackedBy) { player ->
            if (!npcs.owns(player) || player.gameMode == GameMode.SPECTATOR) return@nearestWithin null
            val at = player.eyeLocation
            if (at.world != eye.world) null else at.distanceSquared(eye)
        }
        if (target != null) {
            val at = target.eyeLocation
            entity.lookAt(at.x, at.y, at.z, LookAnchor.EYES)
            following = true
        } else if (following) {
            entity.setRotation(homeYaw, homePitch)
            following = false
        }
    }
}
