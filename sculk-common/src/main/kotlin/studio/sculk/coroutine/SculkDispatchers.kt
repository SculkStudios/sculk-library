package studio.sculk.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatchers backed by Sculk's Folia-aware [SculkScheduler].
 *
 * - [main] resumes continuations on the server's main / global-region thread — the only
 *   place it is safe to touch most of the Paper API. On Folia this is the global region
 *   thread; for entity- or location-specific work prefer the scheduler overloads directly.
 * - [async] resumes continuations off the main thread for blocking IO (database, HTTP).
 *
 * ```kotlin
 * scope.launchAsync {
 *     val profile = repo.find(uuid)          // off-thread
 *     withMain { player.sendMessage("Hi") }  // back on the main thread
 * }
 * ```
 */
@SculkStable
public class SculkDispatchers internal constructor(scheduler: SculkScheduler) {
    /** Dispatches onto the main / global-region thread via [SculkScheduler.runSync]. */
    public val main: CoroutineDispatcher = SchedulerDispatcher { task -> scheduler.runSync(task) }

    /** Dispatches onto an async worker thread via [SculkScheduler.runAsync]. */
    public val async: CoroutineDispatcher = SchedulerDispatcher { task -> scheduler.runAsync(task) }

    private class SchedulerDispatcher(private val submit: (Runnable) -> Unit) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable): Unit = submit(block)
    }
}
