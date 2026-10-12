package studio.sculk.task

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.execution.delayTicks
import studio.sculk.time.SculkClock
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [action] every [periodTicks] in [context], first after [initialDelayTicks], until the job is
 * cancelled or the scope closes. The next wait starts when [action] finishes, so a slow action is
 * never run twice at once.
 */
@SculkStable
public fun SculkScope.repeating(
    periodTicks: Long,
    context: ExecutionContext = ExecutionContext.Global,
    initialDelayTicks: Long = 0,
    action: suspend () -> Unit,
): Job = launch(execution.dispatcher(context)) {
    delayTicks(initialDelayTicks)
    while (isActive) {
        action()
        delayTicks(periodTicks.coerceAtLeast(1))
    }
}

/** Runs [action] once in [context] after [delayTicks]. */
@SculkStable
public fun SculkScope.delayed(delayTicks: Long, context: ExecutionContext = ExecutionContext.Global, action: suspend () -> Unit): Job =
    launch(execution.dispatcher(context)) {
        delayTicks(delayTicks)
        action()
    }

/**
 * Runs [action] on the [cron] schedule in [zone], in [context]. Each wait is recomputed from the
 * [clock], so daylight-saving changes and a slow action never accumulate drift.
 */
@SculkStable
public fun SculkScope.cron(
    cron: CronExpression,
    zone: ZoneId = ZoneId.systemDefault(),
    context: ExecutionContext = ExecutionContext.Global,
    clock: SculkClock = SculkClock.SYSTEM,
    action: suspend () -> Unit,
): Job = launch(execution.dispatcher(context)) {
    while (isActive) {
        val now = clock.now().atZone(zone)
        val next = cron.nextAfter(now) ?: return@launch
        delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
        action()
    }
}

/**
 * Walks a changing collection [perTick] items per tick, resuming where it left off, and starts
 * over once it reaches the end. For work too large for one tick: refreshing every player's
 * display, auditing every loaded chunk.
 */
@SculkStable
public fun <T> SculkScope.batches(
    perTick: Int,
    context: ExecutionContext = ExecutionContext.Global,
    source: () -> Collection<T>,
    action: suspend (T) -> Unit,
): Job {
    require(perTick > 0) { "A batch must process at least one item per tick, but was $perTick." }
    return launch(execution.dispatcher(context)) {
        var cursor = 0
        while (isActive) {
            val snapshot = source().toList()
            if (cursor >= snapshot.size) cursor = 0
            snapshot.subList(cursor, minOf(cursor + perTick, snapshot.size)).forEach { action(it) }
            cursor += perTick
            delayTicks(1)
        }
    }
}

/**
 * Collapses bursts: [action] runs once, [waitTicks] after the last [trigger] of a burst. For
 * saving after a run of edits or reloading after several file events. Thread-safe.
 */
@SculkStable
public class Debouncer(
    private val scope: SculkScope,
    private val waitTicks: Long,
    private val context: ExecutionContext = ExecutionContext.Global,
    private val action: suspend () -> Unit,
) {
    private val pending = AtomicReference<Job?>()

    public fun trigger() {
        val next = scope.delayed(waitTicks, context, action)
        pending.getAndSet(next)?.cancel()
    }

    public fun cancel() {
        pending.getAndSet(null)?.cancel()
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
