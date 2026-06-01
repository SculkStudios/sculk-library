package studio.sculk.coroutine

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler
import kotlin.coroutines.CoroutineContext

/**
 * A plugin-lifecycle-bound [CoroutineScope] for Sculk.
 *
 * Built from the platform's [SculkScheduler], it exposes [dispatchers] for switching between
 * the main thread and async workers, and convenience launchers that pick the right one. The
 * scope uses a [SupervisorJob] so a single failing child never tears down the rest.
 *
 * It is a [SculkHandle]: closing it (done automatically on platform shutdown) cancels every
 * coroutine still running, so plugins never leak background work.
 *
 * ```kotlin
 * sculk.scope.launchAsync {
 *     val data = repo.find(uuid)
 *     withMain { player.sendMessage("Loaded ${data?.coins ?: 0} coins") }
 * }
 * ```
 */
@SculkStable
public class SculkCoroutineScope
@SculkInternal
constructor(scheduler: SculkScheduler, name: String = "sculk") :
    CoroutineScope,
    SculkHandle {
    /** Dispatchers backed by the Folia-aware scheduler. */
    public val dispatchers: SculkDispatchers = SculkDispatchers(scheduler)

    private val job = SupervisorJob()

    // Default to async so plugin background work doesn't accidentally block the main thread.
    override val coroutineContext: CoroutineContext = job + dispatchers.async + CoroutineName(name)

    /** Launches a coroutine on the main / global-region thread. */
    public fun launchMain(block: suspend CoroutineScope.() -> Unit): Job = launch(dispatchers.main, block = block)

    /** Launches a coroutine on an async worker thread. */
    public fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job = launch(dispatchers.async, block = block)

    /** Runs [block] on the main / global-region thread and suspends until it returns. */
    public suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T = withContext(dispatchers.main, block)

    /** Runs [block] on an async worker thread and suspends until it returns. */
    public suspend fun <T> withAsync(block: suspend CoroutineScope.() -> T): T = withContext(dispatchers.async, block)

    /** Cancels every coroutine launched in this scope. Safe to call multiple times. */
    override fun close() {
        job.cancel()
    }
}
