package studio.sculk.paper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.event.player.PlayerTeleportEvent
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.EntityResult
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SectionPosition
import studio.sculk.execution.WorldKey
import studio.sculk.execution.currentExecution
import studio.sculk.execution.withEntity
import studio.sculk.execution.withExecutionContext

/** Runs [block] on the thread owning [entity], following it across regions; [EntityResult.Gone] if it was removed. */
@SculkStable
public suspend fun <T> withEntity(entity: Entity, block: suspend CoroutineScope.() -> T): EntityResult<T> = withEntity(entity.ref, block)

/** Runs [block] on the thread owning the chunk at [location]. */
@SculkStable
public suspend fun <T> withRegion(location: Location, block: suspend CoroutineScope.() -> T): T =
    withExecutionContext(location.regionContext(currentSectionShift()), block)

/**
 * Teleports this entity from the thread that owns it and returns whether the teleport happened.
 * Code after the call runs on the thread owning the entity at its destination, which on Folia and
 * Canvas is usually a different region from where it started.
 */
@SculkStable
public suspend fun Entity.teleportTo(
    location: Location,
    cause: PlayerTeleportEvent.TeleportCause = PlayerTeleportEvent.TeleportCause.PLUGIN,
): EntityResult<Boolean> = withEntity(this) { teleportAsync(location, cause).await() }

/**
 * Runs [action] for each item on the thread owning its location, grouping items by region section.
 * Results come back in input order; see [studio.sculk.execution.regionBatch] for the guarantees.
 */
@SculkStable
public suspend fun <T, R> regionBatch(items: List<T>, locationOf: (T) -> Location, action: suspend (T) -> R): List<R> {
    val shift = currentSectionShift()
    return studio.sculk.execution.regionBatch(items, { locationOf(it).regionContext(shift) }, action)
}

/** The region context for this location at [sectionShift]. Throws for a location without a world. */
@SculkStable
public fun Location.regionContext(sectionShift: Int = SectionPosition.DEFAULT_SHIFT): ExecutionContext.Region {
    val world = requireNotNull(world) { "A location without a world has no owning region: $this" }
    return ExecutionContext.Region(WorldKey(world.key.asString()), SectionPosition.ofBlock(blockX, blockZ, sectionShift))
}

private suspend fun currentSectionShift(): Int =
    (currentExecution().executor as? PaperExecutor)?.sectionShift ?: SectionPosition.DEFAULT_SHIFT
