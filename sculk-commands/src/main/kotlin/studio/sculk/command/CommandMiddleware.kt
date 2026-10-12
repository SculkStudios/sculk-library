package studio.sculk.command

import studio.sculk.annotation.SculkStable
import studio.sculk.time.SculkClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Behaviour around an executor: checks that may stop it, state that spans runs. Middleware runs
 * outermost first, in the executor's context, and calls [proceed] at most once to continue.
 */
@SculkStable
public fun interface CommandMiddleware {
    public suspend fun intercept(context: CommandContext, proceed: suspend () -> Unit)
}

/**
 * Lets each sender run the command once per [duration]. The console and command blocks share one
 * cooldown. Runs that stop with [CommandContext.fail] or an error do not start it.
 */
@SculkStable
public class Cooldown(
    public val duration: Duration,
    private val clock: SculkClock = SculkClock.SYSTEM,
    private val messages: CommandMessages = CommandMessages.DEFAULT,
) : CommandMiddleware {
    private val readyAt = ConcurrentHashMap<String, Long>()

    init {
        require(duration.isPositive()) { "A cooldown must be positive, but was $duration." }
    }

    override suspend fun intercept(context: CommandContext, proceed: suspend () -> Unit) {
        val key = senderKey(context.source)
        val now = clock.monotonicNanos()
        val until = readyAt[key]
        if (until != null && now - until < 0) {
            context.reply(messages.cooldown, "remaining" to readable((until - now).nanoseconds))
            return
        }
        proceed()
        readyAt[key] = clock.monotonicNanos() + duration.inWholeNanoseconds
    }
}

/**
 * Asks the sender to run the command a second time within [window] before it proceeds. For
 * commands that delete or reset something.
 */
@SculkStable
public class Confirmation(
    public val window: Duration,
    private val clock: SculkClock = SculkClock.SYSTEM,
    private val messages: CommandMessages = CommandMessages.DEFAULT,
) : CommandMiddleware {
    private val pending = ConcurrentHashMap<String, Pair<String, Long>>()

    init {
        require(window.isPositive()) { "A confirmation window must be positive, but was $window." }
    }

    override suspend fun intercept(context: CommandContext, proceed: suspend () -> Unit) {
        val key = senderKey(context.source)
        val now = clock.monotonicNanos()
        val waiting = pending.remove(key)
        if (waiting != null && waiting.first == context.input && now - waiting.second < 0) {
            proceed()
            return
        }
        pending[key] = context.input to now + window.inWholeNanoseconds
        context.reply(messages.confirm, "window" to readable(window))
    }
}

private fun senderKey(source: CommandSource): String = source.uniqueId?.toString() ?: "${source.kind}:${source.name}"

/** Rounded up to whole seconds, so "0s" is never shown while the wait is still running. */
internal fun readable(duration: Duration): String {
    val seconds = duration.toDouble(DurationUnit.SECONDS).let { kotlin.math.ceil(it).toLong() }.coerceAtLeast(1)
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val rest = seconds % 60
    return buildString {
        if (hours > 0) append(hours).append('h')
        if (minutes > 0) append(minutes).append('m')
        if (rest > 0 || isEmpty()) append(rest).append('s')
    }
}
