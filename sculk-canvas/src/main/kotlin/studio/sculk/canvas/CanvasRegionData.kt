package studio.sculk.canvas

import io.canvasmc.canvas.region.RegionTickData
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.bukkit.Server
import org.bukkit.World
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.WrongContextException
import studio.sculk.runtime.SculkLog

/**
 * Creates region-local state on Canvas: one value per region, merged when regions merge and split
 * when they split (THREADING.md, Canvas). A region's value is created the first time it is read on
 * that region's thread, and only values that exist are merged or split.
 *
 * The merge and split callbacks run while Canvas holds its region lock. They must be quick, must not
 * block, suspend or touch the world, and must not throw: an exception there leaves the server in an
 * unrecoverable state, so Sculk catches it, logs it and carries on. In development mode a callback
 * slower than [CALLBACK_BUDGET_NANOS] is reported.
 */
@SculkStable
public class CanvasRegionData internal constructor(
    private val server: Server,
    private val log: SculkLog,
    private val developmentMode: Boolean,
) {
    /**
     * Registers a kind of region-local state.
     *
     * [create] makes the value for a new region. [merge] folds [from] into [into]; deadlines in
     * [from] are `tickOffset` ticks behind [into]'s clock. [split] distributes [from] over the new
     * regions, looking each one up by chunk through [RegionTarget].
     */
    public fun <T : Any> create(
        create: (world: World) -> T,
        merge: (from: T, into: T, tickOffset: Long) -> Unit,
        split: (from: T, target: RegionTarget<T>) -> Unit,
    ): RegionData<T> {
        val callback = object : RegionTickData.IRegionizedData.IRegionizedCallback<T> {
            override fun merge(from: T, into: T, fromTickOffset: Long) = guarded("merge") { merge(from, into, fromTickOffset) }

            override fun split(
                from: T,
                chunkToRegionShift: Int,
                regionToData: Long2ReferenceOpenHashMap<T>,
                dataSet: ReferenceOpenHashSet<T>,
            ) = guarded("split") {
                val target = object : RegionTarget<T> {
                    override val all: Collection<T> = dataSet

                    override fun forChunk(chunkX: Int, chunkZ: Int): T? =
                        regionToData.get(getRegionSectionCoordinates(chunkX, chunkZ, chunkToRegionShift))
                }
                split(from, target)
            }
        }
        val handle = server.createRegionizedData({ _, world -> create(world) }, callback)
        return RegionData(server, handle)
    }

    private inline fun guarded(phase: String, block: () -> Unit) {
        val started = System.nanoTime()
        try {
            block()
        } catch (failure: Throwable) {
            log.error("A regionized data $phase callback threw; Canvas requires these callbacks never to throw.", failure)
        }
        val elapsed = System.nanoTime() - started
        if (developmentMode && elapsed > CALLBACK_BUDGET_NANOS) {
            log.warn(
                "A regionized data $phase callback took ${elapsed / 1_000} µs under Canvas's region lock; keep it under ${CALLBACK_BUDGET_NANOS / 1_000} µs.",
            )
        }
    }

    @SculkStable
    public companion object {
        /** One millisecond: a merge or split is meant to move a few references, not to compute. */
        public const val CALLBACK_BUDGET_NANOS: Long = 1_000_000
    }
}

/** One kind of region-local state; read it with [local] from a region thread. */
@SculkStable
public class RegionData<T : Any> internal constructor(private val server: Server, private val handle: RegionTickData.IRegionizedData<T>) {
    /**
     * The value for the region the calling thread is ticking. Off a region thread this is a bug and
     * throws [WrongContextException]; run it inside `withRegion` or `withEntity`.
     */
    public fun local(): T = try {
        server.getLocalRegionizedData(handle)
    } catch (failure: IllegalStateException) {
        throw WrongContextException("Reading regionized data", "a region thread", failure)
    }
}

/** The regions a split produces. */
@SculkStable
public interface RegionTarget<T : Any> {
    /** Every new region's value. */
    public val all: Collection<T>

    /** The value of the new region that owns the chunk, or null when no new region does. */
    public fun forChunk(chunkX: Int, chunkZ: Int): T?
}
