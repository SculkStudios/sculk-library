package studio.sculk.discord

import studio.sculk.annotation.SculkStable
import java.util.ArrayDeque

/**
 * A sliding one-minute window, for staying under a self-imposed send rate.
 *
 * Not a substitute for the backend's own handling of Discord's 429s — that is the backend's job, and
 * it knows the bucket headers. This is the coarser control an operator wants: a burst of a thousand
 * incidents in a second should post a handful and drop the rest, rather than queue a thousand
 * requests that arrive over the following ten minutes describing something that stopped happening.
 *
 * [now] is injectable so the behaviour is testable without sleeping.
 */
@SculkStable
public class RateLimiter(private val now: () -> Long = System::currentTimeMillis) {
    private val stamps = ArrayDeque<Long>()

    /**
     * Claims a slot, returning false when [maxPerMinute] have already been taken.
     *
     * Claim this immediately before sending, never when deciding whether to build a message: a slot
     * burned on a send that then turned out to be impossible is a slot a real alert needed.
     */
    @Synchronized
    public fun acquire(maxPerMinute: Int): Boolean {
        val cutoff = now() - WINDOW_MILLIS
        while (stamps.isNotEmpty() && stamps.peekFirst() <= cutoff) {
            stamps.pollFirst()
        }
        if (stamps.size >= maxPerMinute.coerceAtLeast(1)) return false
        stamps.addLast(now())
        return true
    }

    /** Slots taken in the last minute. */
    @Synchronized
    public fun used(): Int {
        val cutoff = now() - WINDOW_MILLIS
        while (stamps.isNotEmpty() && stamps.peekFirst() <= cutoff) {
            stamps.pollFirst()
        }
        return stamps.size
    }

    private companion object {
        const val WINDOW_MILLIS = 60_000L
    }
}
