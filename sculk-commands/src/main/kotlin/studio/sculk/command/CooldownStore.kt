package studio.sculk.command

import studio.sculk.annotation.SculkInternal
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe per-key cooldown tracker backing [CommandNode.cooldown].
 *
 * Keys are usually `"<cooldownKey>:<senderId>"`. [tryAcquire] both checks and arms the
 * cooldown in one atomic step, so concurrent command dispatches can't slip past it.
 */
@SculkInternal
public class CooldownStore {
    private val expiries = ConcurrentHashMap<String, Long>()

    /**
     * Attempts to start the cooldown for [key] lasting [durationMillis].
     *
     * Returns `null` when the cooldown was free (and is now armed), or the remaining
     * milliseconds when it is still active.
     */
    public fun tryAcquire(
        key: String,
        durationMillis: Long,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        var remaining: Long? = null
        expiries.compute(key) { _, existing ->
            if (existing != null && existing > now) {
                remaining = existing - now
                existing
            } else {
                now + durationMillis
            }
        }
        return remaining
    }

    /** Clears all tracked cooldowns. */
    public fun clear(): Unit = expiries.clear()
}
