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
 * so cleanup after an abandoned dispatch must not touch the world (THREADING.md). Only a refusal
 * ([IllegalStateException], see [SculkExecutor.schedule]) is abandoned this way; any other
 * exception from the executor is a programmer error and propagates.
 *
 * Dispatchers are equal when they share an executor and a context, so `withContext` re-entering the
 * context the coroutine is already in takes kotlinx's undispatched fast path. Entity dispatchers are
 * the exception: see [equals].
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
        } catch (refused: IllegalStateException) {
            abandon(context, block, CancellationException("${executor.platform} refused work in ${this.context}.", refused))
        }
    }

    /**
     * Equal for the same executor and context, except that entity dispatchers are equal only to
     * themselves: re-entering an entity must still ask [SculkExecutor.owns], so an entity removed in
     * the meantime yields [EntityResult.Gone] instead of running inline (THREADING.md rule 3).
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContextDispatcher || context is ExecutionContext.Entity) return false
        return executor === other.executor && context == other.context
    }

    override fun hashCode(): Int = if (context is ExecutionContext.Entity) System.identityHashCode(this) else context.hashCode()

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
