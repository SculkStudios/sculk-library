package studio.sculk.npc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/** Lets one click per player through per [cooldownNanos]. Thread-safe: players click from their own regions. */
internal class ClickGate(private val cooldownNanos: Long) {
    private val last = ConcurrentHashMap<UUID, Long>()

    /** Whether a click by [player] at monotonic time [now] counts. */
    fun pass(player: UUID, now: Long): Boolean {
        if (cooldownNanos == 0L) return true
        var counted = false
        last.compute(player) { _, previous ->
            if (previous == null || now - previous >= cooldownNanos) {
                counted = true
                now
            } else {
                previous
            }
        }
        return counted
    }

    fun forget(player: UUID) {
        last.remove(player)
    }

    val size: Int get() = last.size
}

/** A chunk an NPC keeps loaded. */
internal data class ChunkKey(val world: UUID, val x: Int, val z: Int)

/**
 * Counts NPCs per chunk, because Paper keeps one plugin ticket per chunk and plugin: the first NPC
 * in a chunk adds it and only the last one out may remove it. Thread-safe across regions.
 */
internal class ChunkTickets {
    private val counts = ConcurrentHashMap<ChunkKey, Int>()

    /** Counts one more NPC in [chunk]; true when it is the first and the ticket must be added. */
    fun acquire(chunk: ChunkKey): Boolean = counts.merge(chunk, 1, Int::plus) == 1

    /** Counts one NPC fewer in [chunk]; true when it was the last and the ticket must be removed. */
    fun release(chunk: ChunkKey): Boolean {
        var last = false
        counts.computeIfPresent(chunk) { _, count ->
            if (count <= 1) {
                last = true
                null
            } else {
                count - 1
            }
        }
        return last
    }

    fun count(chunk: ChunkKey): Int = counts[chunk] ?: 0
}

/**
 * Player skins by UUID, each looked up once however many NPCs wear it. A lookup that finds nothing
 * or fails is forgotten, so the next NPC asks again. Thread-safe.
 */
internal class SkinCache(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    private val lookup: suspend (UUID) -> SkinTexture?,
) {
    private val resolved = ConcurrentHashMap<UUID, SkinTexture>()
    private val pending = ConcurrentHashMap<UUID, Deferred<SkinTexture?>>()

    /** The texture for [skin] when it is known without a lookup. */
    fun known(skin: Skin): SkinTexture? = when (skin) {
        is Skin.Textures -> SkinTexture(skin.value, skin.signature)
        is Skin.OfPlayer -> resolved[skin.uuid]
        Skin.Default -> null
    }

    /** The texture of [uuid]'s skin, looking it up unless another caller already is. */
    suspend fun resolve(uuid: UUID): SkinTexture? {
        resolved[uuid]?.let { return it }
        val created = scope.async(context, CoroutineStart.LAZY) { lookup(uuid) }
        val lookingUp = pending.putIfAbsent(uuid, created)
        if (lookingUp != null) created.cancel() else created.start()
        val deferred = lookingUp ?: created
        return try {
            deferred.await().also { texture -> if (texture != null) resolved[uuid] = texture }
        } finally {
            // A caller cancelled while waiting leaves the entry to the next caller, who finds it complete.
            if (deferred.isCompleted) pending.remove(uuid, deferred)
        }
    }
}

/** The candidate [distanceSquared] puts nearest within [range], skipping those it maps to null. */
internal fun <T> nearestWithin(range: Double, candidates: Iterable<T>, distanceSquared: (T) -> Double?): T? {
    var best: T? = null
    var bestDistance = range * range
    for (candidate in candidates) {
        val distance = distanceSquared(candidate) ?: continue
        if (distance <= bestDistance) {
            best = candidate
            bestDistance = distance
        }
    }
    return best
}
