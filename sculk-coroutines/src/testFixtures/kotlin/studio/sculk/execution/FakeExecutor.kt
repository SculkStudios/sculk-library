package studio.sculk.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A deterministic [SculkExecutor] for tests. Nothing runs until the test calls [tick]; work then
 * runs on the calling thread, in scheduling order, and [owns] answers as the chosen [model] would
 * on a real server. Wrong-context bugs that would race on Folia fail here, in a unit test.
 */
@SculkStable
public class FakeExecutor(
    public val model: ThreadModel = ThreadModel.REGIONIZED,
    override val platform: String = "Fake${model.name.lowercase().replaceFirstChar(Char::uppercaseChar)}",
) : SculkExecutor {
    /** How contexts map to threads. */
    @SculkStable
    public enum class ThreadModel {
        /** Paper: one main thread owns global, every region and every entity. */
        SINGLE_THREAD,

        /** Folia and Canvas: every region, entity and the global region are separate owners. */
        REGIONIZED,

        /** Velocity: no game threads; only proxy and async work. */
        PROXY,
    }

    private class Pending(
        val context: ExecutionContext,
        var dueTick: Long,
        val periodTicks: Long?,
        val work: SculkExecutor.Work,
        val sequence: Long,
    ) {
        var cancelled = false
    }

    private val lock = Any()
    private val pending = mutableListOf<Pending>()
    private val removed = mutableSetOf<UUID>()
    private var sequence = 0L
    private var running: ExecutionContext? = null
    private var shutDown = false

    /** The current tick; starts at 0 and advances with [tick]. */
    public var currentTick: Long = 0
        private set

    /** Every context work ran in, in order, for assertions. */
    public val history: MutableList<ExecutionContext> = mutableListOf()

    override fun supports(context: ExecutionContext): Boolean = when (model) {
        ThreadModel.PROXY -> context == ExecutionContext.Proxy || context == ExecutionContext.Async
        else -> context != ExecutionContext.Proxy
    }

    override fun owns(context: ExecutionContext): Boolean {
        val current = running ?: return false
        return when (model) {
            ThreadModel.SINGLE_THREAD -> if (context ==
                ExecutionContext.Async
            ) {
                current == ExecutionContext.Async
            } else {
                current != ExecutionContext.Async
            }

            else -> current == context
        }
    }

    override fun schedule(context: ExecutionContext, delayTicks: Long, work: SculkExecutor.Work): SculkHandle =
        enqueue(context, delayTicks, null, work)

    override fun scheduleRepeating(context: ExecutionContext, delayTicks: Long, periodTicks: Long, work: SculkExecutor.Work): SculkHandle =
        enqueue(context, delayTicks, periodTicks.coerceAtLeast(1), work)

    private fun enqueue(context: ExecutionContext, delayTicks: Long, periodTicks: Long?, work: SculkExecutor.Work): SculkHandle {
        if (!supports(context)) throw UnsupportedContextException(context, platform)
        val entity = (context as? ExecutionContext.Entity)?.entity
        val task = synchronized(lock) {
            check(!shutDown) { "$platform has shut down." }
            if (entity != null && entity.id in removed) {
                null
            } else {
                Pending(context, currentTick + delayTicks.coerceAtLeast(1), periodTicks, work, sequence++).also { pending += it }
            }
        }
        if (task == null) {
            work.retired()
            return SculkHandle.NONE
        }
        return SculkHandle { synchronized(lock) { task.cancelled = true } }
    }

    /** Removes [entity]: its pending work is retired now and later work is retired on scheduling. */
    public fun remove(entity: EntityRef) {
        val retiring = synchronized(lock) {
            removed += entity.id
            pending.filter { (it.context as? ExecutionContext.Entity)?.entity?.id == entity.id && !it.cancelled }
                .onEach { pending.remove(it) }
        }
        retiring.forEach { it.work.retired() }
    }

    /** Advances [count] ticks, running all work due in each. */
    public fun tick(count: Int = 1) {
        repeat(count) {
            currentTick++
            while (true) {
                val due = synchronized(lock) {
                    pending.filter { it.dueTick <= currentTick && !it.cancelled }.minByOrNull { it.sequence }?.also { task ->
                        pending.remove(task)
                        task.periodTicks?.let { period ->
                            task.dueTick = currentTick + period
                            pending += Pending(task.context, task.dueTick, period, task.work, sequence++)
                        }
                    }
                } ?: break
                execute(due)
            }
            synchronized(lock) { pending.removeAll { it.cancelled } }
        }
    }

    private fun execute(task: Pending) {
        val previous = running
        running = task.context
        history += task.context
        try {
            task.work.run()
        } finally {
            running = previous
        }
    }

    /**
     * Ticks until [done] holds. Between ticks it also waits briefly for continuations that finish
     * on background threads, such as work abandoned because its entity was removed. Fails after
     * [maxTicks] ticks or [maxWaitMillis] of waiting with nothing scheduled.
     */
    public fun tickUntil(maxTicks: Int = 1_000, maxWaitMillis: Long = 2_000, done: () -> Boolean) {
        var ticks = 0
        var waited = 0L
        while (!done()) {
            if (pendingCount > 0) {
                tick()
                if (++ticks > maxTicks) throw AssertionError("The condition did not hold within $maxTicks ticks.")
            } else {
                Thread.sleep(WAIT_STEP_MILLIS)
                waited += WAIT_STEP_MILLIS
                if (waited > maxWaitMillis) throw AssertionError("The condition did not hold and nothing is scheduled.")
            }
        }
    }

    /** Pending work that has not been cancelled. */
    public val pendingCount: Int get() = synchronized(lock) { pending.count { !it.cancelled } }

    /** Further scheduling throws, as on a server that is stopping. */
    public fun shutdown() {
        synchronized(lock) { shutDown = true }
    }

    /**
     * Runs [block] in a fresh root scope, ticking until it completes, and returns its result.
     * Fails after [maxTicks] ticks without completion. The scope is closed afterwards.
     */
    public fun <T> run(maxTicks: Int = 1_000, block: suspend CoroutineScope.() -> T): T {
        val failures = mutableListOf<Throwable>()
        val scope = SculkScope.root("test", this) { failures += it }
        try {
            val result = scope.async(block = block).asCompletableFuture()
            var ticks = 0
            while (!result.isDone) {
                if (pendingCount > 0) {
                    tick()
                    if (++ticks > maxTicks) throw AssertionError("The coroutine did not complete within $maxTicks ticks.")
                } else {
                    // Abandoned continuations finish on Dispatchers.IO, outside the tick loop.
                    try {
                        result.get(IDLE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
                    } catch (_: TimeoutException) {
                        if (pendingCount == 0) throw AssertionError("The coroutine is suspended with nothing scheduled to resume it.")
                    }
                }
            }
            failures.firstOrNull()?.let { throw it }
            return try {
                result.get()
            } catch (failed: ExecutionException) {
                throw failed.cause ?: failed
            }
        } finally {
            scope.close()
        }
    }

    private companion object {
        const val IDLE_WAIT_MILLIS = 200L
        const val WAIT_STEP_MILLIS = 5L
    }
}

/** An [EntityRef] for tests. */
@SculkStable
public data class FakeEntity(override val description: String, override val id: UUID = UUID.randomUUID()) : EntityRef
