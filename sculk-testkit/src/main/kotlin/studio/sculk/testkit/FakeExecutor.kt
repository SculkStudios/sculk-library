package studio.sculk.testkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.EntityRef
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.SculkScope
import studio.sculk.execution.SectionPosition
import studio.sculk.execution.UnsupportedContextException
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A deterministic [SculkExecutor] for tests. Nothing runs until the test calls [tick]; work then
 * runs on the ticking thread, in scheduling order, and [owns] answers as the chosen [model] would
 * on a real server. Wrong-context bugs that would race on Folia fail here, in a unit test.
 *
 * Ownership is per thread, as on a server: work running in a tick owns its context on the ticking
 * thread only, so a continuation resumed elsewhere is never mistaken for its owner.
 */
@SculkStable
public class FakeExecutor(
    public val model: ThreadModel = ThreadModel.REGIONIZED,
    override val regionShift: Int = SectionPosition.DEFAULT_SHIFT,
    override val platform: String = model.platform,
) : SculkExecutor {
    /** How contexts map to threads. */
    @SculkStable
    public enum class ThreadModel(internal val platform: String) {
        /** Paper: one main thread owns global, every region and every entity. */
        SINGLE_THREAD("Fake Paper"),

        /** Folia and Canvas: every region section, every entity and the global region are separate owners. */
        REGIONIZED("Fake Folia"),

        /** Velocity: no game threads; only proxy and async work. */
        PROXY("Fake Velocity"),
    }

    private class Pending(val context: ExecutionContext, var dueTick: Long, val periodTicks: Long?, val work: SculkExecutor.Work) {
        var sequence = 0L

        @Volatile
        var cancelled = false
    }

    private val lock = Any()
    private val pending = mutableListOf<Pending>()
    private val removed = mutableSetOf<UUID>()
    private val ran = mutableListOf<ExecutionContext>()
    private val running = ThreadLocal<ExecutionContext?>()
    private var sequence = 0L
    private var shutDown = false

    /** The current tick; starts at 0 and advances with [tick]. */
    @Volatile
    public var currentTick: Long = 0
        private set

    /** Every context work ran in, in order, for assertions. */
    public val history: List<ExecutionContext> get() = synchronized(lock) { ran.toList() }

    /** Pending work that has not been cancelled. */
    public val pendingCount: Int get() = synchronized(lock) { pending.count { !it.cancelled } }

    override fun supports(context: ExecutionContext): Boolean = when (model) {
        ThreadModel.PROXY -> context == ExecutionContext.Proxy || context == ExecutionContext.Async
        else -> context != ExecutionContext.Proxy
    }

    override fun owns(context: ExecutionContext): Boolean {
        val current = running.get() ?: return false
        return when (model) {
            ThreadModel.SINGLE_THREAD -> (context == ExecutionContext.Async) == (current == ExecutionContext.Async)
            ThreadModel.REGIONIZED -> sameOwner(current, context)
            ThreadModel.PROXY -> current == context
        }
    }

    private fun sameOwner(a: ExecutionContext, b: ExecutionContext): Boolean = when {
        a is ExecutionContext.Region && b is ExecutionContext.Region ->
            a.world == b.world &&
                a.section(regionShift) == b.section(regionShift)

        a is ExecutionContext.Entity && b is ExecutionContext.Entity -> a.entity.id == b.entity.id

        else -> a == b
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
                Pending(context, currentTick + delayTicks.coerceAtLeast(1), periodTicks, work).also {
                    it.sequence = sequence++
                    pending += it
                }
            }
        }
        if (task == null) {
            work.retired()
            return SculkHandle.NONE
        }
        return SculkHandle { task.cancelled = true }
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

    /** Advances [count] ticks on the calling thread, running all work due in each. */
    public fun tick(count: Int = 1) {
        repeat(count) {
            currentTick++
            while (true) {
                val due = synchronized(lock) { takeDue() } ?: break
                execute(due)
            }
            synchronized(lock) { pending.removeAll { it.cancelled } }
        }
    }

    private fun takeDue(): Pending? {
        val task = pending.filter { it.dueTick <= currentTick && !it.cancelled }.minByOrNull { it.sequence } ?: return null
        val period = task.periodTicks
        if (period == null) {
            pending.remove(task)
        } else {
            task.dueTick = currentTick + period
            task.sequence = sequence++
        }
        ran += task.context
        return task
    }

    private fun execute(task: Pending) {
        val previous = running.get()
        running.set(task.context)
        try {
            task.work.run()
        } finally {
            running.set(previous)
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

    /** Retires every pending task and makes further scheduling throw, as a stopping server does. */
    public fun shutdown() {
        val retiring = synchronized(lock) {
            shutDown = true
            pending.filter { !it.cancelled }.also { pending.clear() }
        }
        retiring.forEach { it.work.retired() }
    }

    /**
     * Runs [block] in a fresh root scope, ticking until it completes, and returns its result.
     * Fails after [maxTicks] ticks, or after [maxWaitMillis] with nothing scheduled to resume it.
     * The scope is closed afterwards.
     */
    public fun <T> run(maxTicks: Int = 1_000, maxWaitMillis: Long = 2_000, block: suspend CoroutineScope.() -> T): T {
        val failures = mutableListOf<Throwable>()
        val scope = SculkScope.root("test", this) { synchronized(failures) { failures += it } }
        try {
            val result = scope.async(block = block).asCompletableFuture()
            var ticks = 0
            var waited = 0L
            while (!result.isDone) {
                if (pendingCount > 0) {
                    tick()
                    if (++ticks > maxTicks) throw AssertionError("The coroutine did not complete within $maxTicks ticks.")
                } else {
                    try {
                        result.get(WAIT_STEP_MILLIS, TimeUnit.MILLISECONDS)
                    } catch (_: TimeoutException) {
                        waited += WAIT_STEP_MILLIS
                        if (waited > maxWaitMillis && pendingCount == 0) {
                            throw AssertionError("The coroutine is suspended with nothing scheduled to resume it.")
                        }
                    } catch (_: ExecutionException) {
                        // Completed exceptionally; rethrown below with its own cause.
                    }
                }
            }
            synchronized(failures) { failures.firstOrNull() }?.let { throw it }
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
        const val WAIT_STEP_MILLIS = 5L
    }
}

/** An [EntityRef] for tests. */
@SculkStable
public class FakeEntity(override val description: String, override val id: UUID = UUID.randomUUID()) : EntityRef {
    override fun equals(other: Any?): Boolean = other is FakeEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = description
}
