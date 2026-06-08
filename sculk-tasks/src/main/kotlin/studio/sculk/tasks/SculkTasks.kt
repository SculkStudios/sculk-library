package studio.sculk.tasks

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.coroutine.SculkCoroutineScope
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.function.Consumer

private const val MILLIS_PER_TICK = 50L

/**
 * Coroutine-based scheduling built on the platform [SculkCoroutineScope].
 *
 * Repeating, cron, debounce, and throttle helpers all run their action on the main /
 * global-region thread (Folia-correct) and return a [SculkHandle] for cancellation. Every task is a
 * child of the plugin scope, so all of them stop automatically when the platform closes.
 *
 * ```kotlin
 * sculk.tasks.repeating(intervalTicks = 20) { broadcast("<gray>tick") }
 * sculk.tasks.cron("0 3 * * *") { runNightlyBackup() }
 * ```
 */
@SculkStable
public class SculkTasks
@SculkInternal
constructor(private val scope: SculkCoroutineScope) {
    /**
     * Runs [action] every [intervalTicks] ticks (20 ticks = 1 second) after [initialDelayTicks].
     * The action runs on the main thread.
     */
    @SculkStable
    public fun repeating(intervalTicks: Long, initialDelayTicks: Long = 0, action: suspend () -> Unit): SculkHandle {
        require(intervalTicks > 0) { "Interval must be positive." }
        val job =
            scope.launchMain {
                if (initialDelayTicks > 0) delay(initialDelayTicks * MILLIS_PER_TICK)
                while (isActive) {
                    action()
                    delay(intervalTicks * MILLIS_PER_TICK)
                }
            }
        return SculkHandle { job.cancel() }
    }

    /**
     * Java-friendly overload of [repeating]. The [action] runs on the main thread.
     *
     * ```java
     * sculk.getTasks().repeating(20L, 0L, () -> Bukkit.broadcast(Component.text("tick")));
     * ```
     */
    @JvmOverloads
    @SculkStable
    public fun repeating(intervalTicks: Long, initialDelayTicks: Long = 0, action: Runnable): SculkHandle =
        repeating(intervalTicks, initialDelayTicks) { action.run() }

    /** Runs [action] once after [delayTicks] ticks, on the main thread. */
    @SculkStable
    public fun delayed(delayTicks: Long, action: suspend () -> Unit): SculkHandle {
        val job =
            scope.launchMain {
                delay(delayTicks * MILLIS_PER_TICK)
                action()
            }
        return SculkHandle { job.cancel() }
    }

    /** Java-friendly overload of [delayed]. The [action] runs on the main thread. */
    @SculkStable
    public fun delayed(delayTicks: Long, action: Runnable): SculkHandle = delayed(delayTicks) { action.run() }

    /**
     * Runs [action] on the schedule described by the cron [expression] (see [CronExpression]).
     * Timing is computed off-thread; the action runs on the main thread.
     */
    @SculkStable
    public fun cron(expression: String, zone: ZoneId = ZoneId.systemDefault(), action: suspend () -> Unit): SculkHandle {
        val cron = CronExpression.parse(expression)
        val job =
            scope.launchAsync {
                while (isActive) {
                    val now = ZonedDateTime.now(zone)
                    val next = cron.nextAfter(now) ?: break
                    delay(
                        java.time.Duration
                            .between(now, next)
                            .toMillis()
                            .coerceAtLeast(1),
                    )
                    if (!isActive) break
                    scope.withMain { action() }
                }
            }
        return SculkHandle { job.cancel() }
    }

    /**
     * Java-friendly overload of [cron]. [zone] defaults to the system zone; the [action] runs on
     * the main thread.
     */
    @JvmOverloads
    @SculkStable
    public fun cron(expression: String, zone: ZoneId = ZoneId.systemDefault(), action: Runnable): SculkHandle =
        cron(expression, zone) { action.run() }

    /**
     * Returns a function that delays running [action] until [waitMillis] have passed without
     * another call — the last call within a burst wins. Useful for "save after the player stops
     * editing" style behaviour.
     */
    @SculkStable
    public fun <T> debounce(waitMillis: Long, action: suspend (T) -> Unit): (T) -> Unit {
        var pending: Job? = null
        return { value ->
            pending?.cancel()
            pending =
                scope.launchMain {
                    delay(waitMillis)
                    action(value)
                }
        }
    }

    /**
     * Java-friendly overload of [debounce]. Returns a [Consumer] that debounces calls to [action].
     *
     * ```java
     * Consumer<UUID> save = sculk.getTasks().debounce(1000L, uuid -> persist(uuid));
     * save.accept(playerId);
     * ```
     */
    @SculkStable
    public fun <T> debounce(waitMillis: Long, action: Consumer<T>): Consumer<T> {
        val fn = debounce<T>(waitMillis) { action.accept(it) }
        return Consumer { fn(it) }
    }

    /**
     * Returns a function that runs [action] immediately, then ignores further calls until
     * [intervalMillis] have elapsed — rate-limits bursty triggers.
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

    /**
     * Java-friendly overload of [throttle]. Returns a [Runnable] that rate-limits calls to [action].
     */
    @SculkStable
    public fun throttle(intervalMillis: Long, action: Runnable): Runnable {
        val fn = throttle(intervalMillis) { action.run() }
        return Runnable { fn() }
    }
}
