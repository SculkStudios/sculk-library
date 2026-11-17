package studio.sculk

import studio.sculk.annotation.SculkStable
import kotlin.coroutines.cancellation.CancellationException

/**
 * The outcome of an operation that can fail for reasons the caller is expected to handle: I/O,
 * user input, a missing capability. Programmer errors throw instead.
 *
 * A failure is itself a result ([SculkFailure]), so `when (result)` is exhaustive over
 * [Success] and [SculkFailure] without a wrapper.
 */
@SculkStable
public sealed interface SculkResult<out T> {
    @SculkStable
    public data class Success<out T>(public val value: T) : SculkResult<T>

    @SculkStable
    public companion object {
        private val UNIT = Success(Unit)

        /** A success holding [value]. */
        public fun <T> success(value: T): SculkResult<T> = Success(value)

        /** The shared successful [Unit] result. */
        public fun ok(): SculkResult<Unit> = UNIT

        /**
         * Runs [block] and turns any exception into [SculkFailure.OperationFailed] described as
         * "Could not [what]". Cancellation is rethrown, never captured as a failure; an interrupt is
         * captured but the thread stays interrupted, so the caller can still see it.
         */
        public inline fun <T> catching(what: String, block: () -> T): SculkResult<T> = try {
            Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            SculkFailure.OperationFailed(what, error)
        }
    }
}

/** True for [SculkResult.Success]. */
@SculkStable
public val SculkResult<*>.isSuccess: Boolean get() = this is SculkResult.Success

/** True for a [SculkFailure]. */
@SculkStable
public val SculkResult<*>.isFailure: Boolean get() = this is SculkFailure

/** The value, or null for a failure. */
@SculkStable
public fun <T> SculkResult<T>.getOrNull(): T? = (this as? SculkResult.Success)?.value

/** The failure, or null for a success. */
@SculkStable
public fun SculkResult<*>.failureOrNull(): SculkFailure? = this as? SculkFailure

/** Returns the value, or throws [SculkFailureException] carrying the failure. */
@SculkStable
public fun <T> SculkResult<T>.getOrThrow(): T = when (this) {
    is SculkResult.Success -> value
    is SculkFailure -> throw SculkFailureException(this)
}

/** The value, or what [fallback] returns for the failure. */
@SculkStable
public inline fun <T> SculkResult<T>.getOrElse(fallback: (SculkFailure) -> T): T = when (this) {
    is SculkResult.Success -> value
    is SculkFailure -> fallback(this)
}

/** A success with [transform] applied to the value; a failure unchanged, without calling [transform]. */
@SculkStable
public inline fun <T, R> SculkResult<T>.map(transform: (T) -> R): SculkResult<R> = when (this) {
    is SculkResult.Success -> SculkResult.Success(transform(value))
    is SculkFailure -> this
}

/** For a success, whatever [transform] returns for the value, success or failure; a failure unchanged, without calling [transform]. */
@SculkStable
public inline fun <T, R> SculkResult<T>.flatMap(transform: (T) -> SculkResult<R>): SculkResult<R> = when (this) {
    is SculkResult.Success -> transform(value)
    is SculkFailure -> this
}

/** [onSuccess] of the value or [onFailure] of the failure, whichever applies. */
@SculkStable
public inline fun <T, R> SculkResult<T>.fold(onSuccess: (T) -> R, onFailure: (SculkFailure) -> R): R = when (this) {
    is SculkResult.Success -> onSuccess(value)
    is SculkFailure -> onFailure(this)
}

/** This success unchanged, or a success holding what [fallback] returns for the failure. */
@SculkStable
public inline fun <T> SculkResult<T>.recover(fallback: (SculkFailure) -> T): SculkResult<T> = when (this) {
    is SculkResult.Success -> this
    is SculkFailure -> SculkResult.Success(fallback(this))
}

/** Calls [action] with the value of a success; returns this result either way. */
@SculkStable
public inline fun <T> SculkResult<T>.onSuccess(action: (T) -> Unit): SculkResult<T> {
    if (this is SculkResult.Success) action(value)
    return this
}

/** Calls [action] with the failure; returns this result either way. */
@SculkStable
public inline fun <T> SculkResult<T>.onFailure(action: (SculkFailure) -> Unit): SculkResult<T> {
    if (this is SculkFailure) action(this)
    return this
}

/** Thrown by [getOrThrow]; carries the failure so nothing about it is lost. */
@SculkStable
public class SculkFailureException(public val failure: SculkFailure) : RuntimeException(failure.describe(), failure.cause)
