package studio.sculk.execution

import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import studio.sculk.annotation.SculkStable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * The executor a coroutine schedules through. Sculk scopes carry it in their context, which is how
 * [withGlobal], [withRegion], [withEntity] and [delayTicks] find the platform without a receiver.
 */
@SculkStable
public class SculkExecution(public val executor: SculkExecutor) : AbstractCoroutineContextElement(Key) {
    private val global = ContextDispatcher(executor, ExecutionContext.Global)
    private val async = ContextDispatcher(executor, ExecutionContext.Async)
    private val proxy = ContextDispatcher(executor, ExecutionContext.Proxy)

    /** Where a scope's coroutines start by default: the global region on a server, the plugin executor on a proxy. */
    public val home: CoroutineDispatcher = if (executor.supports(ExecutionContext.Global)) global else proxy

    /**
     * The dispatcher for [context]. Throws [UnsupportedContextException] at once when the platform
     * has no thread for it, so a region on a proxy fails where it is written, not when it first runs.
     */
    public fun dispatcher(context: ExecutionContext): CoroutineDispatcher {
        if (!executor.supports(context)) throw UnsupportedContextException(context, executor.platform)
        return when (context) {
            ExecutionContext.Global -> global
            ExecutionContext.Async -> async
            ExecutionContext.Proxy -> proxy
            is ExecutionContext.Region, is ExecutionContext.Entity -> ContextDispatcher(executor, context)
        }
    }

    override fun toString(): String = "SculkExecution(${executor.platform})"

    @SculkStable
    public companion object Key : CoroutineContext.Key<SculkExecution>
}

/** The outcome of entity work: it ran, or the entity was removed before it could. */
@SculkStable
public sealed interface EntityResult<out T> {
    @SculkStable
    public data class Ran<out T>(public val value: T) : EntityResult<T>

    @SculkStable
    public data class Gone(public val entity: EntityRef) : EntityResult<Nothing>
}

@SculkStable
public fun <T> EntityResult<T>.getOrNull(): T? = (this as? EntityResult.Ran)?.value

/** The entity a coroutine was running for was removed. Cancels only the work bound to that entity. */
@SculkStable
@OptIn(ExperimentalCoroutinesApi::class)
public class EntityGoneException(public val entity: EntityRef) :
    CancellationException("${entity.description} was removed before the work bound to it could run."),
    CopyableThrowable<EntityGoneException> {
    override fun createCopy(): EntityGoneException = EntityGoneException(entity).also { it.initCause(this) }
}

/** The execution of the current Sculk scope; outside one, a wiring bug that throws. */
@SculkStable
public suspend fun currentExecution(): SculkExecution = currentCoroutineContext()[SculkExecution]
    ?: throw IllegalStateException("This code is not running in a Sculk scope; launch it from the runtime's scope.")

/** Runs [block] in [context] and returns its result. Runs inline when the caller already owns it. */
@SculkStable
public suspend fun <T> withExecutionContext(context: ExecutionContext, block: suspend CoroutineScope.() -> T): T =
    withContext(currentExecution().dispatcher(context), block)

/** Runs [block] on the global region: time, weather, game rules, console commands. */
@SculkStable
public suspend fun <T> withGlobal(block: suspend CoroutineScope.() -> T): T = withExecutionContext(ExecutionContext.Global, block)

/** Runs [block] off every game thread, for I/O and computation. */
@SculkStable
public suspend fun <T> withAsync(block: suspend CoroutineScope.() -> T): T = withExecutionContext(ExecutionContext.Async, block)

/** Runs [block] on the thread owning chunk [chunkX], [chunkZ] of [world]. */
@SculkStable
public suspend fun <T> withRegion(world: WorldKey, chunkX: Int, chunkZ: Int, block: suspend CoroutineScope.() -> T): T =
    withExecutionContext(ExecutionContext.Region(world, chunkX, chunkZ), block)

/**
 * Runs [block] on the thread owning [entity], following it across regions. Returns
 * [EntityResult.Gone] instead of throwing when the entity is removed before or while [block] runs.
 */
@SculkStable
public suspend fun <T> withEntity(entity: EntityRef, block: suspend CoroutineScope.() -> T): EntityResult<T> = try {
    EntityResult.Ran(withExecutionContext(ExecutionContext.Entity(entity), block))
} catch (gone: EntityGoneException) {
    if (gone.entity.id != entity.id) throw gone
    currentCoroutineContext().ensureActive()
    EntityResult.Gone(entity)
}

/**
 * Suspends for [ticks] server ticks and resumes in the same execution context; zero or less waits
 * for the next tick, as every Sculk delay does. On a proxy or in [ExecutionContext.Async] a tick is
 * [SculkExecutor.MILLIS_PER_TICK] of wall-clock time. Kotlin's `delay` keeps measuring wall-clock
 * time; use this where alignment with ticks matters.
 *
 * Cancelled, rather than failed, when the executor refuses the wait because it is shutting down.
 */
@SculkStable
public suspend fun delayTicks(ticks: Long) {
    val coroutineContext = currentCoroutineContext()
    val execution = coroutineContext[SculkExecution] ?: throw IllegalStateException("delayTicks needs a Sculk scope.")
    val context = (coroutineContext[ContinuationInterceptor] as? ContextDispatcher)?.context ?: ExecutionContext.Async
    suspendCancellableCoroutine { continuation ->
        val wake = object : SculkExecutor.Work {
            override fun run() = continuation.resume(Unit)

            override fun retired() {
                continuation.cancel(ContextDispatcher.retirement(context))
            }
        }
        val handle = try {
            execution.executor.schedule(context, ticks.coerceAtLeast(1), wake)
        } catch (rejected: RuntimeException) {
            continuation.cancel(CancellationException("${execution.executor.platform} refused a wait in $context.", rejected))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation { handle.close() }
    }
}
