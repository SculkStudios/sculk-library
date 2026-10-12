package studio.sculk.execution

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resumes coroutines in one [ExecutionContext]. Dispatches only when the current thread does not
 * already own the context, so code that is home stays in the same tick.
 *
 * A dispatch is never lost. When the entity has left or the executor has shut down, the job is
 * cancelled first and the continuation then resumes on [Dispatchers.IO], where it observes the
 * cancellation instead of running its body on a thread that does not own anything.
 */
internal class ContextDispatcher(private val executor: SculkExecutor, val context: ExecutionContext) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !executor.owns(this.context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val work = object : SculkExecutor.Work {
            override fun run() = block.run()

            override fun retired() = abandon(context, block, entityGone())
        }
        try {
            executor.schedule(this.context, 0, work)
        } catch (rejected: RuntimeException) {
            abandon(context, block, CancellationException("${executor.platform} rejected work in ${this.context}.", rejected))
        }
    }

    private fun entityGone(): CancellationException {
        val entity = (context as? ExecutionContext.Entity)?.entity ?: return CancellationException("$context was retired.")
        return EntityGoneException(entity)
    }

    private fun abandon(context: CoroutineContext, block: Runnable, cause: CancellationException) {
        context.cancel(cause)
        Dispatchers.IO.dispatch(context, block)
    }

    override fun toString(): String = "Sculk($context)"
}
