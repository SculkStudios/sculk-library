package studio.sculk.core.coroutine

import kotlinx.coroutines.suspendCancellableCoroutine
import studio.sculk.core.annotation.SculkStable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until this [CompletableFuture] completes, returning its value or rethrowing its failure.
 *
 * Cancelling the surrounding coroutine cancels the future. Bridges the few Paper APIs that still
 * hand back futures (e.g. async chunk loads, profile lookups) into Sculk's coroutine surface.
 */
@SculkStable
public suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        whenComplete { value, error ->
            when (error) {
                null -> cont.resume(value)
                is CancellationException -> cont.cancel(error)
                else -> cont.resumeWithException(error.cause ?: error)
            }
        }
        cont.invokeOnCancellation { cancel(true) }
    }
