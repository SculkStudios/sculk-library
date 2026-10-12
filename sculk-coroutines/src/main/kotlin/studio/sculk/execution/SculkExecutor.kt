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

    /**
     * Region sections are `2^regionShift` chunks wide; two chunks in one section always share an
     * owner, so work grouped by section runs in one region. Folia's and Canvas's `grid-exponent`.
     * Any value on a server with one main thread; unused on a proxy.
     */
    public val regionShift: Int

    /** True when this platform has a thread for [context]. A proxy has no regions or entities. */
    public fun supports(context: ExecutionContext): Boolean

    /** True when the calling thread owns [context] and may touch what it guards directly. */
    public fun owns(context: ExecutionContext): Boolean

    /**
     * Runs [work] in [context] after [delayTicks]. The returned handle cancels it.
     *
     * For [ExecutionContext.Entity], exactly one of [Work.run] and [Work.retired] is called; retired
     * means the entity was removed first. Work of any context still pending when the executor shuts
     * down is retired, never silently dropped. Throws [UnsupportedContextException] for a context the
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

/**
 * Code ran on a thread that does not own what it touched. [requirement] names what it needed, such
 * as `Global` or `a region thread`.
 */
@SculkStable
public class WrongContextException(public val operation: String, public val requirement: String, cause: Throwable? = null) :
    IllegalStateException("$operation requires $requirement, but the calling thread does not own it.", cause)

/** Throws [WrongContextException] unless the calling thread owns [context]. */
@SculkStable
public fun SculkExecutor.checkOwned(context: ExecutionContext, operation: String) {
    if (!owns(context)) throw WrongContextException(operation, context.toString())
}
