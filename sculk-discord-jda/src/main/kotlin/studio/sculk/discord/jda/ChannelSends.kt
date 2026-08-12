package studio.sculk.discord.jda

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import studio.sculk.SculkResult
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serialises sends per channel, and retries the failures that are worth retrying.
 *
 * Two problems, one place, because the fix for both is a queue.
 *
 * **Order.** Nothing about a bare `RestAction` promises that two messages dispatched a millisecond
 * apart arrive in that order — they land in rate-limit buckets and race. For a chat relay that is
 * immediately visible: two players talking produces an exchange that reads backwards in Discord.
 * A fair mutex per channel makes each channel a queue while leaving different channels independent,
 * so a slow console relay cannot hold up chat.
 *
 * **Transience.** A 502 from Discord's edge means try again; a 403 means the bot cannot post there and
 * will not be able to on the tenth attempt either. Retrying everything turns a permissions mistake
 * into a burst of requests against a rate limit, and retrying nothing drops messages to a blip. So the
 * classification is explicit, and the default for anything unrecognised is *not* to retry.
 *
 * Bounded on purpose: a channel that has stopped accepting messages must not accumulate an unbounded
 * backlog of coroutines waiting to find that out. Past [MAX_WAITING] the send is refused by name, and
 * the caller — which usually has a webhook fallback — gets to decide.
 */
internal class ChannelSends {
    private val channels = ConcurrentHashMap<String, Slot>()

    private class Slot {
        val mutex = Mutex()
        val waiting = AtomicInteger(0)
    }

    suspend fun <T> ordered(channel: String, what: String, block: suspend () -> T): SculkResult<T> {
        val slot = acquire(channel)
            ?: return SculkResult.failure(
                "Channel $channel already has $MAX_WAITING sends waiting, so this one was refused rather " +
                    "than queued behind them. Discord is refusing or throttling this channel.",
            )
        try {
            return slot.mutex.withLock { attemptWithRetry(what, block) }
        } finally {
            release(channel)
        }
    }

    /**
     * Claims a place in the channel's queue, or refuses when it is already too long.
     *
     * Claiming and releasing both go through `compute`, which holds the map's per-bin lock for the
     * duration. That matters: the obvious version — `computeIfAbsent`, then increment — lets a slot be
     * dropped in between by a departing sender, after which the next arrival builds a *second* slot for
     * the same channel and the two hold different mutexes. Ordering, the one thing this class exists
     * for, would then be lost exactly when the channel is busiest.
     */
    private fun acquire(channel: String): Slot? {
        var refused = false
        val slot = channels.compute(channel) { _, existing ->
            val target = existing ?: Slot()
            if (target.waiting.get() >= MAX_WAITING) {
                refused = true
                target
            } else {
                target.also { it.waiting.incrementAndGet() }
            }
        }
        return slot.takeUnless { refused }
    }

    /** Releases a place, dropping the slot once nobody is using it. */
    private fun release(channel: String) {
        channels.compute(channel) { _, existing ->
            existing?.takeIf { it.waiting.decrementAndGet() > 0 }
        }
    }

    private suspend fun <T> attemptWithRetry(what: String, block: suspend () -> T): SculkResult<T> {
        val first = runCatching { block() }
        first.getOrNull()?.let { return SculkResult.success(it) }
        val error = first.exceptionOrNull() ?: return SculkResult.failure("Could not $what.")
        if (!error.isTransient()) {
            return SculkResult.failure("Could not $what: ${error.message ?: error::class.simpleName}", error)
        }

        delay(RETRY_DELAY_MILLIS)
        return runCatching { block() }.fold(
            { SculkResult.success(it) },
            { retryError ->
                SculkResult.failure(
                    "Could not $what after a retry: ${retryError.message ?: retryError::class.simpleName}",
                    retryError,
                )
            },
        )
    }

    private companion object {
        const val MAX_WAITING = 64
        const val RETRY_DELAY_MILLIS = 500L
        const val SERVER_ERROR = 500

        /**
         * Whether trying again could plausibly work.
         *
         * Deliberately a short allow-list rather than "anything that is not a 4xx": an unrecognised
         * failure is not retried, so a new failure mode costs a dropped message rather than a storm of
         * requests nobody asked for.
         */
        fun Throwable.isTransient(): Boolean = generateSequence(this, Throwable::cause).take(CAUSE_DEPTH).any { error ->
            when (error) {
                is IOException -> true
                is ErrorResponseException -> error.response.code >= SERVER_ERROR || error.response.isRateLimit
                else -> false
            }
        }

        const val CAUSE_DEPTH = 5
    }
}
