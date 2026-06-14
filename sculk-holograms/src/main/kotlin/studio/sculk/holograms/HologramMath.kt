package studio.sculk.holograms

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** Bukkit-free helpers for [HologramService], split out so the core logic is unit-testable. */
internal object HologramMath {
    /** Chunk search radius needed to cover [maxViewRangeBlocks]. Always at least one chunk. */
    fun chunkRadius(maxViewRangeBlocks: Double): Int = ceil(maxViewRangeBlocks / 16.0).toInt().coerceAtLeast(1)

    /** Viewers present in [desired] but not yet in [current]. */
    fun <K> toAdd(current: Set<K>, desired: Set<K>): List<K> = desired.filter { it !in current }

    /** Viewers present in [current] but no longer [desired]. */
    fun <K> toRemove(current: Set<K>, desired: Set<K>): List<K> = current.filter { it !in desired }
}

/** Allocates collision-resistant virtual entity ids, counting down from [start]. */
internal class EntityIdAllocator(start: Int = Int.MAX_VALUE) {
    private val counter = AtomicInteger(start)

    fun next(): Int = counter.getAndDecrement()
}
