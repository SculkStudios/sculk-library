package studio.sculk.task

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.execution.delayTicks
import studio.sculk.time.SculkClock
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [action] every [periodTicks] in [context], first after [initialDelayTicks], until the job is
 * cancelled or the scope closes. Delays below one tick mean the next tick. The next wait starts
 * when [action] finishes, so a slow action is never run twice at once.
 *
 * A run that throws is reported to the scope's failure handler and the schedule continues, as a
 * server's repeating task does; cancel the job to stop it.
 */
@SculkStable
public fun SculkScope.repeating(
    periodTicks: Long,
    context: ExecutionContext = ExecutionContext.Global,
    initialDelayTicks: Long = 1,
    action: suspend () -> Unit,
): Job = startIn(context) {
    delayTicks(initialDelayTicks)
    while (currentCoroutineContext().isActive) {
        runReporting(action)
        delayTicks(periodTicks)
    }
}

/** Runs [action] once in [context] after [delayTicks]; below one tick means the next tick. */
@SculkStable
public fun SculkScope.delayed(delayTicks: Long, context: ExecutionContext = ExecutionContext.Global, action: suspend () -> Unit): Job =
    startIn(context) {
        delayTicks(delayTicks)
        action()
    }

/**
 * Runs [action] on the [cron] schedule in [zone], in [context]. Each wait is recomputed from the
 * [clock], so daylight-saving changes and a slow action never accumulate drift. A run that throws
 * is reported and the schedule continues.
 */
@SculkStable
public fun SculkScope.cron(
    cron: CronExpression,
    zone: ZoneId = ZoneId.systemDefault(),
    context: ExecutionContext = ExecutionContext.Global,
    clock: SculkClock = SculkClock.SYSTEM,
    action: suspend () -> Unit,
): Job = startIn(context) {
    while (currentCoroutineContext().isActive) {
        val now = clock.now().atZone(zone)
        val next = checkNotNull(cron.nextAfter(now)) { "'$cron' has no future run." }
        delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
        runReporting(action)
    }
}

/**
 * Walks a changing collection [perTick] items per tick, resuming where it left off, and starts
 * over once it reaches the end. For work too large for one tick: refreshing every player's
 * display, auditing every loaded chunk. The first items are read on the next tick, in [context].
 * An item whose action throws is reported and skipped.
 */
@SculkStable
public fun <T> SculkScope.batches(
    perTick: Int,
    context: ExecutionContext = ExecutionContext.Global,
    source: () -> Collection<T>,
    action: suspend (T) -> Unit,
): Job {
    require(perTick > 0) { "A batch must process at least one item per tick, but was $perTick." }
    return startIn(context) {
        var cursor = 0
        while (currentCoroutineContext().isActive) {
            delayTicks(1)
            val snapshot = source().toList()
            if (cursor >= snapshot.size) cursor = 0
            snapshot.subList(cursor, minOf(cursor + perTick, snapshot.size)).forEach { runReporting { action(it) } }
            cursor += perTick
        }
    }
}

/**
 * Collapses bursts: [action] runs once, [waitTicks] after the last [trigger] of a burst. For
 * saving after a run of edits or reloading after several file events.
 *
 * A trigger only ever cancels a wait, never a run in progress: a burst that arrives while
 * [action] runs schedules one more run after it. Runs never overlap. Thread-safe.
 */
@SculkStable
public class Debouncer(
    private val scope: SculkScope,
    private val waitTicks: Long,
    private val context: ExecutionContext = ExecutionContext.Global,
    private val action: suspend () -> Unit,
) {
    private val lock = Any()
    private val running = Mutex()
    private var waiting: Job? = null

    public fun trigger() {
        synchronized(lock) {
            waiting?.cancel()
            var self: Job? = null
            self = scope.delayed(waitTicks, context) {
                val claimed = synchronized(lock) { (waiting === self).also { if (it) waiting = null } }
                if (claimed) running.withLock { action() }
            }
            waiting = self
        }
    }

    /** Cancels a pending run. A run already in progress finishes. */
    public fun cancel() {
        synchronized(lock) {
            waiting?.cancel()
            waiting = null
        }
    }
}

/**
 * Lets [action] run at most once per [intervalTicks]: the first [trigger] runs it, later ones
 * inside the interval are dropped. Thread-safe.
 */
@SculkStable
public class Throttler(
    private val scope: SculkScope,
    private val intervalTicks: Long,
    private val clock: SculkClock = SculkClock.SYSTEM,
    private val context: ExecutionContext = ExecutionContext.Global,
    private val action: suspend () -> Unit,
) {
    private val nextAllowedNanos = AtomicLong(Long.MIN_VALUE)

    /** Runs [action] unless it ran within the interval; returns whether it was started. */
    public fun trigger(): Boolean {
        val now = clock.monotonicNanos()
        val allowedAt = nextAllowedNanos.get()
        if (allowedAt != Long.MIN_VALUE && now - allowedAt < 0) return false
        val interval = intervalTicks * NANOS_PER_TICK
        if (!nextAllowedNanos.compareAndSet(allowedAt, now + interval)) return false
        scope.launch(scope.execution.dispatcher(context)) { action() }
        return true
    }

    private companion object {
        const val NANOS_PER_TICK = 50_000_000L
    }
}

/**
 * Starts undispatched so the first wait is scheduled from the caller at once: a delay of n ticks
 * is n ticks, not n plus the tick it would take to reach [context] first.
 */
private fun SculkScope.startIn(context: ExecutionContext, block: suspend () -> Unit): Job =
    launch(execution.dispatcher(context), CoroutineStart.UNDISPATCHED) { block() }

private suspend fun runReporting(action: suspend () -> Unit) {
    try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        val context = currentCoroutineContext()
        context[CoroutineExceptionHandler]?.handleException(context, failure) ?: throw failure
    }
}
