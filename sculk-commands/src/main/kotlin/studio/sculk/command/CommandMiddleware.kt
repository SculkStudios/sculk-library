package studio.sculk.command

import studio.sculk.annotation.SculkStable
import studio.sculk.time.SculkClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
 * Lets each sender run the command once per [duration], counted from the end of a run; while a run
 * is still going, the same sender is refused too. Runs that stop with [CommandContext.fail], an
 * error or cancellation do not start it. Senders are told apart by unique id, and senders without
 * one, such as the console or a command block, by kind and name, so command blocks that share a
 * name share a cooldown.
 */
@SculkStable
public class Cooldown(
    public val duration: Duration,
    private val clock: SculkClock = SculkClock.SYSTEM,
    private val messages: CommandMessages = CommandMessages.DEFAULT,
) : CommandMiddleware {
    private val windows = ConcurrentHashMap<String, Window>()
    private val sweeper: Sweeper

    init {
        require(duration.isPositive() && duration.isFinite()) { "A cooldown must be positive and finite, but was $duration." }
        sweeper = Sweeper(duration.inWholeNanoseconds, clock.monotonicNanos())
    }

    /** Senders currently held: running, or cooling down and not yet swept. */
    internal val trackedSenders: Int get() = windows.size

    override suspend fun intercept(context: CommandContext, proceed: suspend () -> Unit) {
        val key = senderKey(context.source)
        val now = clock.monotonicNanos()
        sweeper.sweep(windows, now) { !it.running && now - it.until >= 0 }
        val reservation = Window(now + duration.inWholeNanoseconds, running = true)
        var holding: Window? = null
        // Reserved before proceed() suspends, so a second run during a suspending body sees it.
        windows.compute(key) { _, current ->
            if (current != null && (current.running || now - current.until < 0)) current.also { holding = it } else reservation
        }
        holding?.let { current ->
            context.reply(messages.cooldown, "remaining" to readable((current.until - now).nanoseconds))
            return
        }
        var completed = false
        try {
            proceed()
            completed = true
        } finally {
            if (completed) {
                windows.replace(key, reservation, Window(clock.monotonicNanos() + duration.inWholeNanoseconds, running = false))
            } else {
                windows.remove(key, reservation)
            }
        }
    }

    // Compared by identity: two runs can reserve at the same instant with the same deadline.
    private class Window(val until: Long, val running: Boolean)
}

/**
 * Asks the sender to run the command a second time within [window] before it proceeds. For
 * commands that delete or reset something. Senders are told apart as [Cooldown] tells them apart.
 */
@SculkStable
public class Confirmation(
    public val window: Duration,
    private val clock: SculkClock = SculkClock.SYSTEM,
    private val messages: CommandMessages = CommandMessages.DEFAULT,
) : CommandMiddleware {
    private val pending = ConcurrentHashMap<String, Pair<String, Long>>()
    private val sweeper: Sweeper

    init {
        require(window.isPositive() && window.isFinite()) { "A confirmation window must be positive and finite, but was $window." }
        sweeper = Sweeper(window.inWholeNanoseconds, clock.monotonicNanos())
    }

    /** Senders with a request still waiting and not yet swept. */
    internal val trackedSenders: Int get() = pending.size

    override suspend fun intercept(context: CommandContext, proceed: suspend () -> Unit) {
        val key = senderKey(context.source)
        val now = clock.monotonicNanos()
        sweeper.sweep(pending, now) { now - it.second >= 0 }
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

/**
 * Drops expired entries at most once per [period], so senders who never come back do not stay in
 * memory; an entry outlives its expiry by at most one period.
 */
private class Sweeper(private val period: Long, start: Long) {
    private val lastSweep = AtomicLong(start)

    fun <V : Any> sweep(entries: ConcurrentHashMap<String, V>, now: Long, expired: (V) -> Boolean) {
        val last = lastSweep.get()
        if (now - last < period || !lastSweep.compareAndSet(last, now)) return
        // Removes only the value it tested, so an entry replaced meanwhile survives.
        entries.values.removeIf { expired(it) }
    }
}

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
