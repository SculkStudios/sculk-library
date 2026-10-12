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
 * A dispatch is never lost. When the entity has left or the executor refuses the work, the job is
 * cancelled first and the continuation then resumes on [ABANDONED], where it observes the
 * cancellation instead of running its body. Its `finally` blocks run there too, owning nothing,
 * so cleanup after an abandoned dispatch must not touch the world (THREADING.md).
 */
internal class ContextDispatcher(private val executor: SculkExecutor, val context: ExecutionContext) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !executor.owns(this.context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val work = object : SculkExecutor.Work {
            override fun run() = block.run()

            override fun retired() = abandon(context, block, retirement(this@ContextDispatcher.context))
        }
        try {
            executor.schedule(this.context, 0, work)
        } catch (rejected: RuntimeException) {
            abandon(context, block, CancellationException("${executor.platform} refused work in ${this.context}.", rejected))
        }
    }

    private fun abandon(context: CoroutineContext, block: Runnable, cause: CancellationException) {
        context.cancel(cause)
        ABANDONED.dispatch(context, block)
    }

    override fun toString(): String = "Sculk($context)"

    companion object {
        /** One named thread at a time, so abandoned cleanup is serialised and easy to spot in a profile. */
        val ABANDONED: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1, "sculk-abandoned")

        fun retirement(context: ExecutionContext): CancellationException = when (context) {
            is ExecutionContext.Entity -> EntityGoneException(context.entity)
            else -> CancellationException("$context was retired.")
        }
    }
}
