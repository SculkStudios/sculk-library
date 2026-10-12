package studio.sculk.execution

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable

/**
 * The platform's scheduler, seen through ownership. Implemented once per platform; everything else
 * in Sculk schedules through it.
 *
 * Implementations normalise timing at this boundary: a delay below one tick means the next tick,
 * a period below one tick means every tick. [ExecutionContext.Async] delays are wall-clock at
 * [MILLIS_PER_TICK] per tick.
 */
@SculkStable
public interface SculkExecutor {
    /** A name for diagnostics, such as `Paper` or `Folia`. */
    public val platform: String

    /** True when this platform has a thread for [context]. A proxy has no regions or entities. */
    public fun supports(context: ExecutionContext): Boolean

    /** True when the calling thread owns [context] and may touch what it guards directly. */
    public fun owns(context: ExecutionContext): Boolean

    /**
     * Runs [work] in [context] after [delayTicks]. The returned handle cancels it.
     *
     * For [ExecutionContext.Entity], exactly one of [Work.run] and [Work.retired] is called; retired
     * means the entity was removed first. Throws [UnsupportedContextException] for a context the
     * platform does not [support][supports], and [IllegalStateException] once the executor has shut down.
     */
    public fun schedule(context: ExecutionContext, delayTicks: Long, work: Work): SculkHandle

    /** Runs [work] every [periodTicks] in [context], first after [delayTicks], until cancelled or retired. */
    public fun scheduleRepeating(context: ExecutionContext, delayTicks: Long, periodTicks: Long, work: Work): SculkHandle

    /** Work an executor runs. [retired] runs instead of [run] when an entity left before its turn. */
    @SculkStable
    public interface Work {
        public fun run()

        /**
         * Called in critical code on Paper and its forks: complete outcomes here, never touch the
         * world, load chunks or remove entities.
         */
        public fun retired() {}
    }

    @SculkStable
    public companion object {
        public const val MILLIS_PER_TICK: Long = 50
    }
}

/** A platform was asked for a context it has no thread for, such as a region on a proxy. */
@SculkStable
public class UnsupportedContextException(public val context: ExecutionContext, platform: String) :
    UnsupportedOperationException("$platform cannot run work in $context.")

/** Code ran in a context that does not own what it touched. Raised by [checkOwned]. */
@SculkStable
public class WrongContextException(public val required: ExecutionContext, operation: String) :
    IllegalStateException("$operation requires $required, but the calling thread does not own it.")

/** Throws [WrongContextException] unless the calling thread owns [context]. */
@SculkStable
public fun SculkExecutor.checkOwned(context: ExecutionContext, operation: String) {
    if (!owns(context)) throw WrongContextException(context, operation)
}
