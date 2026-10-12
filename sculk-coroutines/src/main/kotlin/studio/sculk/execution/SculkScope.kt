package studio.sculk.execution

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import studio.sculk.ResourceScope
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async as launchAsync
import kotlinx.coroutines.launch as launchJob

/**
 * A [ResourceScope] with coroutines attached. Closing it first cancels every coroutine started in
 * it, then closes its resources newest first, so no coroutine runs against a resource that is
 * already closed. Plugin, player, entity and session lifetimes are each one.
 *
 * Coroutines start in the platform's home context, [ExecutionContext.Global] on a server and
 * [ExecutionContext.Proxy] on a proxy, unless told otherwise; never silently off-thread.
 * A failure in one coroutine is reported and does not cancel its siblings.
 *
 * [close] does not wait for cancelled coroutines to finish unwinding: on Folia the closing thread
 * may own the region they need. Their `finally` blocks run as their dispatchers allow.
 */
@SculkStable
public class SculkScope private constructor(public val resources: ResourceScope, override val coroutineContext: CoroutineContext) :
    CoroutineScope,
    SculkHandle {
    public val name: String get() = resources.name

    public val execution: SculkExecution get() = checkNotNull(coroutineContext[SculkExecution])

    public val isClosed: Boolean get() = resources.isClosed

    /** A scope that closes with this one, or earlier on its own. */
    public fun child(name: String): SculkScope = attach(resources.child(name), coroutineContext + SupervisorJob(coroutineContext[Job]))

    /** Starts a coroutine in this scope. Starting one after the scope closed is a bug and throws. */
    public fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        check(!isClosed) { "Scope '$name' is closed; the coroutine would never run." }
        return launchJob(context, start, block)
    }

    /** Starts a coroutine with a result in this scope. Starting one after the scope closed throws. */
    public fun <T> async(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T,
    ): Deferred<T> {
        check(!isClosed) { "Scope '$name' is closed; the coroutine would never run." }
        return launchAsync(context, start, block)
    }

    override fun close(): Unit = resources.close()

    override fun toString(): String = "SculkScope($name)"

    @SculkStable
    public companion object {
        /**
         * A scope with no parent, for a plugin or a proxy plugin. [onFailure] receives every
         * uncaught failure of a coroutine in this scope or any child.
         */
        public fun root(name: String, executor: SculkExecutor, onFailure: (Throwable) -> Unit): SculkScope {
            val execution = SculkExecution(executor)
            val handler = CoroutineExceptionHandler { _, error -> onFailure(error) }
            return attach(ResourceScope.root(name), SupervisorJob() + execution + execution.home + handler)
        }

        private fun attach(resources: ResourceScope, context: CoroutineContext): SculkScope {
            val scope = SculkScope(resources, context)
            resources.onClosing { context.cancel(CancellationException("Scope '${resources.name}' closed.")) }
            return scope
        }
    }
}
