package studio.sculk.canvas

import io.canvasmc.canvas.WorldUnloadResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.withGlobal
import kotlin.coroutines.resume

/** Ownership checks Canvas adds beyond Paper's: whole boxes and moving entities. */
@SculkStable
public class CanvasOwnership internal constructor(private val server: Server) {
    /** True when the calling thread owns every chunk [box] touches in [world]. */
    public fun ownsBox(world: World, box: BoundingBox): Boolean = server.isOwnedByCurrentRegion(world, box)

    /**
     * True when the calling thread owns where something at [location] moving at [velocity] will be,
     * plus [bufferChunks] around it. For projectiles and vehicles about to cross a region edge.
     */
    public fun ownsPath(location: Location, velocity: Vector, bufferChunks: Int = 1): Boolean =
        server.isOwnedByCurrentRegion(location, velocity, bufferChunks)
}

/** Canvas's region-safe world unloading. */
@SculkStable
public class CanvasWorlds internal constructor(private val server: Server) {
    /**
     * Unloads [world] the way Canvas allows: regions finish their teleports, return their players,
     * save and stop, then the global region completes the unload. Returns Canvas's verdict.
     */
    public suspend fun unload(world: World, save: Boolean = true): WorldUnloadResult = withGlobal {
        suspendCancellableCoroutine { continuation ->
            server.unloadWorldAsync(world, save) { result -> continuation.resume(result) }
        }
    }
}
