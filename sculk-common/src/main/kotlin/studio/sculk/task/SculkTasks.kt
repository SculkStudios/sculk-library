package studio.sculk.task

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.coroutine.SculkCoroutineScope
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MILLIS_PER_TICK = 50L

/**
 * Recurring work expressed as suspending functions.
 *
 * The distinction from [studio.sculk.scheduler.SculkScheduler]: the scheduler is the primitive
 * that decides *which thread* a `Runnable` runs on, and everything here is built on it. Reach for
 * [SculkTasks] when the shape of the schedule is the interesting part — a cron string, a debounce
 * window — and for the scheduler when the thread is.
 *
 * Every task is a child of the plugin scope, so all of them stop when the platform closes without
 * the caller keeping a handle. The handle is for stopping one early.
 *
 * ```kotlin
 * sculk.tasks.repeating(intervalTicks = 20) { announceNextEvent() }
 * sculk.tasks.cron("0 3 * * *") { runNightlyBackup() }
 * ```
 */
@SculkStable
public class SculkTasks
@SculkInternal
constructor(private val scope: SculkCoroutineScope) {
    /** Runs [action] on the main thread every [intervalTicks] (20 = one second). */
    @SculkStable
    public fun repeating(intervalTicks: Long, initialDelayTicks: Long = 0, action: suspend () -> Unit): SculkHandle {
        require(intervalTicks > 0) { "Repeating interval must be positive, got $intervalTicks." }
        val job = scope.launchMain {
            if (initialDelayTicks > 0) delay(initialDelayTicks * MILLIS_PER_TICK)
            while (isActive) {
                action()
                delay(intervalTicks * MILLIS_PER_TICK)
            }
        }
        return SculkHandle { job.cancel() }
    }

    /** Runs [action] once on the main thread after [delayTicks]. */
    @SculkStable
    public fun delayed(delayTicks: Long, action: suspend () -> Unit): SculkHandle {
        val job = scope.launchMain {
            delay(delayTicks * MILLIS_PER_TICK)
            action()
        }
        return SculkHandle { job.cancel() }
    }

    /**
     * Runs [action] on the schedule described by the cron [expression] (see [CronExpression]).
     *
     * The wait is computed off-thread and [action] runs on the main thread, because the gap to the
     * next occurrence is usually hours — parking a main-thread coroutine for that long shows up in
     * any thread dump taken during an incident and gets mistaken for a hang.
     */
    @SculkStable
    public fun cron(expression: String, zone: ZoneId = ZoneId.systemDefault(), action: suspend () -> Unit): SculkHandle {
        val cron = CronExpression.parse(expression)
        val job = scope.launchAsync {
            while (isActive) {
                val now = ZonedDateTime.now(zone)
                val next = cron.nextAfter(now) ?: break
                delay(Duration.between(now, next).toMillis().coerceAtLeast(1))
                if (!isActive) break
                scope.withMain { action() }
            }
        }
        return SculkHandle { job.cancel() }
    }

    /**
     * Wraps [action] so it runs only once [waitMillis] have passed with no further call — the last
     * call in a burst wins.
     *
     * For "save after the player stops editing": a menu that persists on every click writes once
     * per click, and a player rearranging a kit produces dozens in a second.
     */
    @SculkStable
    public fun <T> debounce(waitMillis: Long, action: suspend (T) -> Unit): (T) -> Unit {
        var pending: Job? = null
        return { value ->
            pending?.cancel()
            pending = scope.launchMain {
                delay(waitMillis)
                action(value)
            }
        }
    }

    /**
     * Wraps [action] so it runs immediately and then ignores calls until [intervalMillis] elapse.
     *
     * The opposite trade to [debounce]: the *first* call in a burst wins and fires now. Use it
     * where a player is waiting to see something happen, and debounce where nobody is.
     */
    @SculkStable
    public fun throttle(intervalMillis: Long, action: suspend () -> Unit): () -> Unit {
        var lastRun = 0L
        return {
            val now = System.currentTimeMillis()
            if (now - lastRun >= intervalMillis) {
                lastRun = now
                scope.launchMain { action() }
            }
        }
    }
}
