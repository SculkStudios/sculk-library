package studio.sculk.placeholder

import studio.sculk.annotation.SculkStable
import kotlin.time.Duration

/**
 * How fresh a placeholder's value must be. Values are kept per audience, per pair for relational
 * placeholders, and per argument list.
 *
 * For a synchronous placeholder a value that is not fresh is computed again by the render that
 * finds it. For an asynchronous placeholder that render shows the last value and starts a load
 * off the tick thread; see [PlaceholderRegistry.audienceAsync].
 */
@SculkStable
public sealed interface Refresh {
    /** Computed on every render; nothing is kept. An asynchronous placeholder starts a load on every render. */
    @SculkStable
    public data object Live : Refresh

    /** Kept for [interval] after it was computed, or until invalidated. */
    @SculkStable
    public data class Every(public val interval: Duration) : Refresh {
        init {
            require(interval.isPositive() && interval.isFinite()) { "A refresh interval is positive and finite, but was $interval." }
        }
    }

    /** Kept until the placeholder is invalidated, for values that change only on events the plugin sees. */
    @SculkStable
    public data object UntilInvalidated : Refresh
}
