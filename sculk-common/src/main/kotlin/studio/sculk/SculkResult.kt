package studio.sculk

import studio.sculk.annotation.SculkStable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * The outcome of a fallible operation.
 *
 * Sculk uses this instead of [kotlin.Result] because the failures worth reporting here are
 * conditions rather than exceptions — an unknown material name in a config file, an absent
 * packet backend, a query that found nothing. `kotlin.Result` cannot express those without
 * inventing an exception per case, and the exception then has to be unwrapped by every caller
 * that only wanted to know what went wrong.
 *
 * [Failure.message] is read by a server owner in a console log or a player in chat. Write it as
 * a complete sentence naming the value that was wrong, not as an error code:
 * `"No material named 'diamon_sword'; check tools.yml."` rather than `"BAD_MATERIAL"`.
 *
 * ```kotlin
 * when (val result = repo.find(uuid)) {
 *     is SculkResult.Success -> player.sendMessage("Coins: ${result.value?.coins}")
 *     is SculkResult.Failure -> logger.warning(result.message)
 * }
 * ```
 */
@SculkStable
public sealed interface SculkResult<out T> {
    @SculkStable
    public data class Success<T>(public val value: T) : SculkResult<T>

    @SculkStable
    public data class Failure(public val message: String, public val cause: Throwable? = null) : SculkResult<Nothing>

    @SculkStable
    public val isSuccess: Boolean get() = this is Success

    @SculkStable
    public val isFailure: Boolean get() = this is Failure

    @SculkStable
    public fun getOrNull(): T? = (this as? Success)?.value

    /**
     * The value, or throws with the failure message.
     *
     * Reserved for tests and start-up paths where a failure genuinely cannot be handled. In
     * ordinary code it converts a described condition back into an exception, which is the thing
     * this type exists to avoid.
     */
    @SculkStable
    public fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException(message, cause)
    }

    public companion object {
        @SculkStable
        public fun <T> success(value: T): SculkResult<T> = Success(value)

        /**
         * A successful result carrying no value.
         *
         * Returns a shared instance. Write-shaped operations return `SculkResult<Unit>` far more
         * often than anything else, and each `Success(Unit)` was otherwise a fresh allocation on
         * a path that runs per save, per packet, per tick.
         */
        @SculkStable
        public fun ok(): SculkResult<Unit> = UNIT_SUCCESS

        @SculkStable
        public fun failure(message: String, cause: Throwable? = null): SculkResult<Nothing> = Failure(message, cause)

        /**
         * Runs [block], turning anything it throws into a [Failure] described by [describe].
         *
         * The bridge for APIs that report failure by throwing — JDBC, reflection, `URI.create`.
         * Without it every such call site grows its own `runCatching { }.fold(...)`, which is
         * how four of them ended up with four different messages for the same condition.
         *
         * ```kotlin
         * SculkResult.catching("read the player row for $uuid") { statement.executeQuery() }
         * ```
         *
         * [describe] completes the sentence "Failed to …", so phrase it as an action.
         */
        @SculkStable
        @OptIn(ExperimentalContracts::class)
        public inline fun <T> catching(describe: String, block: () -> T): SculkResult<T> {
            contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
            return try {
                Success(block())
            } catch (error: Exception) {
                Failure("Failed to $describe: ${error.message ?: error::class.simpleName}", error)
            }
        }

        private val UNIT_SUCCESS: SculkResult<Unit> = Success(Unit)
    }
}

/**
 * Transforms the value, leaving a failure unchanged.
 *
 * ```kotlin
 * repo.find(uuid).map { it?.coins ?: 0 }
 * ```
 */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T, R> SculkResult<T>.map(transform: (T) -> R): SculkResult<R> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
    return when (this) {
        is SculkResult.Success -> SculkResult.Success(transform(value))
        is SculkResult.Failure -> this
    }
}

/** Chains an operation that is itself fallible, short-circuiting on the first failure. */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T, R> SculkResult<T>.flatMap(transform: (T) -> SculkResult<R>): SculkResult<R> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
    return when (this) {
        is SculkResult.Success -> transform(value)
        is SculkResult.Failure -> this
    }
}

/** Runs [action] on success and returns this unchanged, for use in a chain. */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T> SculkResult<T>.onSuccess(action: (T) -> Unit): SculkResult<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (this is SculkResult.Success) action(value)
    return this
}

/** Runs [action] on failure and returns this unchanged, for use in a chain. */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T> SculkResult<T>.onFailure(action: (message: String, cause: Throwable?) -> Unit): SculkResult<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (this is SculkResult.Failure) action(message, cause)
    return this
}

/** Collapses both arms to a single value. */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T, R> SculkResult<T>.fold(onSuccess: (T) -> R, onFailure: (message: String, cause: Throwable?) -> R): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is SculkResult.Success -> onSuccess(value)
        is SculkResult.Failure -> onFailure(message, cause)
    }
}

/** The value, or [fallback] computed from the failure. */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T> SculkResult<T>.getOrElse(fallback: (message: String, cause: Throwable?) -> T): T {
    contract { callsInPlace(fallback, InvocationKind.AT_MOST_ONCE) }
    return when (this) {
        is SculkResult.Success -> value
        is SculkResult.Failure -> fallback(message, cause)
    }
}

/** Replaces a failure with a successful fallback value. */
@SculkStable
@OptIn(ExperimentalContracts::class)
public inline fun <T> SculkResult<T>.recover(fallback: (message: String, cause: Throwable?) -> T): SculkResult<T> {
    contract { callsInPlace(fallback, InvocationKind.AT_MOST_ONCE) }
    return when (this) {
        is SculkResult.Success -> this
        is SculkResult.Failure -> SculkResult.Success(fallback(message, cause))
    }
}
