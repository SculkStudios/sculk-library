package studio.sculk.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import studio.sculk.SculkHandle

/**
 * A [SculkScheduler] that runs on a tick counter you control instead of a server.
 *
 * Shipped as a test fixture rather than kept private to Sculk's own tests, because a module that
 * hands out an interface should hand out something to test against it with — otherwise every
 * consumer writes this same class again, slightly differently, and their version of "one tick
 * passed" stops agreeing with Sculk's.
 *
 * Undelayed work runs inline so a test can act on its effects immediately. Anything with a delay
 * or a period is queued and fires only from [advance], so "nothing has happened yet" is
 * assertable:
 *
 * ```kotlin
 * val scheduler = FakeScheduler()
 * val hud = HudService(scheduler, messages)
 * hud.start()
 *
 * assertEquals(1, scheduler.pending.size, "the hud drives everything from one task")
 * scheduler.advance(5)
 * assertEquals(1, scheduler.executions)
 * ```
 *
 * Not thread-safe: it exists to remove concurrency from a test, not to model it.
 */
public class FakeScheduler : SculkScheduler {
    /** What [ownsGlobalThread] reports. Flip it to exercise the "wrong thread" branch. */
    public var ownsGlobal: Boolean = true

    /** What both [ownsThread] overloads report. */
    public var ownsRegions: Boolean = true

    public var currentTick: Long = 0
        private set

    /** How many task bodies have run, inline or from [advance]. */
    public var executions: Int = 0
        private set

    private val queue = mutableListOf<Scheduled>()

    /** Queued tasks that have not been cancelled, in scheduling order. */
    public val pending: List<Scheduled> get() = queue.toList()

    /**
     * Advances the clock [ticks] ticks, running everything that falls due.
     *
     * Ticks are stepped one at a time rather than jumped, so a task with a period of 5 fires
     * twice over 10 ticks instead of once.
     */
    public fun advance(ticks: Long) {
        repeat(ticks.toInt()) {
            currentTick++
            // Copied because a firing task may schedule or cancel others.
            for (task in queue.toList()) {
                if (task.cancelled || task.dueTick > currentTick) continue
                task.body.run()
                executions++
                if (task.cancelled) {
                    queue.remove(task)
                } else if (task.periodTicks == null) {
                    queue.remove(task)
                } else {
                    task.dueTick = currentTick + task.periodTicks
                }
            }
        }
    }

    /** Drops every queued task and resets the counters. */
    public fun clear() {
        queue.clear()
        currentTick = 0
        executions = 0
    }

    private fun runInline(task: Runnable): SculkHandle {
        task.run()
        executions++
        return SculkHandle.NONE
    }

    private fun enqueue(delayTicks: Long, periodTicks: Long?, task: Runnable): SculkHandle {
        val scheduled = Scheduled(
            dueTick = currentTick + delayTicks.coerceAtLeast(1),
            periodTicks = periodTicks?.coerceAtLeast(1),
            body = task,
        )
        queue += scheduled
        return SculkHandle {
            scheduled.cancelled = true
            queue.remove(scheduled)
        }
    }

    override fun runSync(task: Runnable): SculkHandle = runInline(task)

    override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = enqueue(delayTicks, null, task)

    override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = enqueue(delayTicks, periodTicks, task)

    override fun runSync(entity: Entity, task: Runnable): SculkHandle = runInline(task)

    override fun runSyncDelayed(entity: Entity, delayTicks: Long, task: Runnable): SculkHandle = enqueue(delayTicks, null, task)

    override fun runSyncRepeating(entity: Entity, delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle =
        enqueue(delayTicks, periodTicks, task)

    override fun runSync(location: Location, task: Runnable): SculkHandle = runInline(task)

    override fun runSyncDelayed(location: Location, delayTicks: Long, task: Runnable): SculkHandle = enqueue(delayTicks, null, task)

    override fun runSyncRepeating(location: Location, delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle =
        enqueue(delayTicks, periodTicks, task)

    override fun ownsThread(entity: Entity): Boolean = ownsRegions

    override fun ownsThread(location: Location): Boolean = ownsRegions

    override fun ownsGlobalThread(): Boolean = ownsGlobal

    override fun runAsync(task: Runnable): SculkHandle = runInline(task)

    override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = enqueue(delayTicks, null, task)

    override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle =
        enqueue(delayTicks, periodTicks, task)

    /** A queued task. [periodTicks] is null for one-shot tasks. */
    public class Scheduled internal constructor(internal var dueTick: Long, public val periodTicks: Long?, internal val body: Runnable) {
        public var cancelled: Boolean = false
            internal set

        public val repeating: Boolean get() = periodTicks != null
    }
}
