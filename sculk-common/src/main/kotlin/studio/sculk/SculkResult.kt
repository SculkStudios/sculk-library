package studio.sculk

import studio.sculk.annotation.SculkStable

/**
 * Represents the outcome of a Sculk Studio operation.
 *
 * [SculkResult] is used throughout the framework to return either a [Success]
 * value or a [Failure] with a descriptive message and optional cause.
 *
 * Example:
 * ```kotlin
 * when (val result = repo.find(uuid)) {
 *     is SculkResult.Success -> println(result.value)
 *     is SculkResult.Failure -> logger.warning(result.message)
 * }
 * ```
 */
@SculkStable
public sealed interface SculkResult<out T> {
    /**
     * A successful result carrying a [value].
     */
    @SculkStable
    public data class Success<T>(public val value: T) : SculkResult<T>

    /**
     * A failed result carrying a [message] and an optional [cause].
     */
    @SculkStable
    public data class Failure(public val message: String, public val cause: Throwable? = null) : SculkResult<Nothing>

    // -------------------------------------------------------------------------
    // Member accessors — first-class from both Kotlin and Java (no `SculkResultKt`).
    // -------------------------------------------------------------------------

    /** True if this is a [Success]. */
    @SculkStable
    public fun isSuccess(): Boolean = this is Success

    /** True if this is a [Failure]. */
    @SculkStable
    public fun isFailure(): Boolean = this is Failure

    /** The success value, or null if this is a [Failure]. */
    @SculkStable
    public fun getOrNull(): T? = (this as? Success)?.value

    /** The success value, or throws [IllegalStateException] (with the failure cause) if this is a [Failure]. */
    @SculkStable
    public fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException(message, cause)
    }

    /**
     * Runs [action] with the value when this is a [Success]; returns this unchanged.
     *
     * ```java
     * repo.save(data)
     *     .ifSuccess(v -> player.sendMessage("<green>Saved!"))
     *     .ifFailure((msg, err) -> getLogger().warning(msg));
     * ```
     */
    @SculkStable
    public fun ifSuccess(action: java.util.function.Consumer<@UnsafeVariance T>): SculkResult<T> {
        if (this is Success) action.accept(value)
        return this
    }

    /** Runs [action] with the message and cause when this is a [Failure]; returns this unchanged. */
    @SculkStable
    public fun ifFailure(action: java.util.function.BiConsumer<String, Throwable?>): SculkResult<T> {
        if (this is Failure) action.accept(message, cause)
        return this
    }

    public companion object {
        /** Wraps [value] in a [Success]. */
        public fun <T> success(value: T): SculkResult<T> = Success(value)

        /** Wraps [message] and optional [cause] in a [Failure]. */
        public fun failure(message: String, cause: Throwable? = null): SculkResult<Nothing> = Failure(message, cause)
    }
}

/**
 * Returns the value if this is a [SculkResult.Success], or [default] otherwise.
 *
 * Kotlin convenience (Java callers use `getOrNull()` with a fallback, since a member returning the
 * default value cannot be expressed safely on the `Failure : SculkResult<Nothing>` arm).
 */
@SculkStable
public fun <T> SculkResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

/** Returns true if this is a [SculkResult.Success]. */
@SculkStable
public val SculkResult<*>.isSuccess: Boolean get() = this is SculkResult.Success

/** Returns true if this is a [SculkResult.Failure]. */
@SculkStable
public val SculkResult<*>.isFailure: Boolean get() = this is SculkResult.Failure

/**
 * Transforms the success value with [transform], leaving failures unchanged.
 *
 * ```kotlin
 * repo.find(uuid)
 *     .map { it?.coins ?: 0 }
 *     .onSuccess { coins -> player.sendMessage("Coins: $coins") }
 * ```
 */
@SculkStable
public fun <T, R> SculkResult<T>.map(transform: (T) -> R): SculkResult<R> = when (this) {
    is SculkResult.Success -> SculkResult.success(transform(value))
    is SculkResult.Failure -> this
}

/**
 * Chains an operation that itself returns a [SculkResult], propagating failures short-circuit style.
 *
 * ```kotlin
 * repo.find(uuid)
 *     .flatMap { data -> if (data != null) SculkResult.success(data) else SculkResult.failure("not found") }
 *     .onSuccess { data -> player.sendMessage("Hello, ${data.name}") }
 * ```
 */
@SculkStable
public fun <T, R> SculkResult<T>.flatMap(transform: (T) -> SculkResult<R>): SculkResult<R> = when (this) {
    is SculkResult.Success -> transform(value)
    is SculkResult.Failure -> this
}

/**
 * Runs [action] with the value if this is a [SculkResult.Success]. Returns this unchanged.
 *
 * ```kotlin
 * repo.save(data).onSuccess { player.sendMessage("<green>Saved!") }
 * ```
 */
@SculkStable
public fun <T> SculkResult<T>.onSuccess(action: (T) -> Unit): SculkResult<T> {
    if (this is SculkResult.Success) action(value)
    return this
}

/**
 * Runs [action] with the message and cause if this is a [SculkResult.Failure]. Returns this unchanged.
 *
 * ```kotlin
 * repo.find(uuid).onFailure { msg, _ -> logger.warning(msg) }
 * ```
 */
@SculkStable
public fun <T> SculkResult<T>.onFailure(action: (message: String, cause: Throwable?) -> Unit): SculkResult<T> {
    if (this is SculkResult.Failure) action(message, cause)
    return this
}

/**
 * Returns [onSuccess] applied to the value if [SculkResult.Success], or [onFailure] if [SculkResult.Failure].
 *
 * ```kotlin
 * val display = result.fold(
 *     onSuccess = { data -> "<green>${data.name}" },
 *     onFailure = { msg, _ -> "<red>Error: $msg" },
 * )
 * ```
 */
@SculkStable
public fun <T, R> SculkResult<T>.fold(onSuccess: (T) -> R, onFailure: (message: String, cause: Throwable?) -> R): R = when (this) {
    is SculkResult.Success -> onSuccess(value)
    is SculkResult.Failure -> onFailure(message, cause)
}

/**
 * Recovers from a failure by computing a fallback value.
 *
 * ```kotlin
 * val data = repo.find(uuid).recover { _, _ -> PlayerData.default(uuid) }
 * ```
 */
@SculkStable
public fun <T> SculkResult<T>.recover(onFailure: (message: String, cause: Throwable?) -> T): SculkResult<T> = when (this) {
    is SculkResult.Success -> this
    is SculkResult.Failure -> SculkResult.success(onFailure(message, cause))
}
