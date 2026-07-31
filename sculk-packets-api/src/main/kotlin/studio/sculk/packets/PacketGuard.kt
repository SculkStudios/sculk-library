package studio.sculk.packets

import studio.sculk.annotation.SculkInternal
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Runs a packet handler so that a bug in it cannot disconnect the player.
 *
 * ### Why this is not optional
 *
 * PacketEvents treats an exception escaping a listener as a malformed packet and **kicks the
 * player**. So a null-pointer in a dig handler does not surface as a stack trace next to the
 * broken code — it surfaces as players being disconnected with a protocol error, which sends
 * everyone looking at the network layer, the proxy, or the client's mods. Every handler call site
 * in every backend goes through here.
 *
 * The packet is deliberately left untouched on failure. Cancelling it would be a guess about what
 * the handler wanted; leaving it alone means the client falls back to vanilla behaviour, which is
 * wrong-but-consistent rather than wrong-and-invisible.
 *
 * ### Why the logging is rate-limited
 *
 * Handlers run on the netty thread, once per packet. A player holding left-click on a broken
 * handler produces a stack trace every tick, and writing those is itself enough to stall the
 * server — the log becomes a second outage on top of the first. One trace per five seconds is
 * enough to diagnose and cheap enough to survive.
 */
@SculkInternal
public class PacketGuard(
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val quietPeriodMillis: Long = 5_000,
) {
    private val lastReport = AtomicLong(Long.MIN_VALUE)

    /** How many handler invocations have thrown. Exposed so a status command can report it. */
    @Volatile
    public var failures: Long = 0
        private set

    /**
     * Runs [block], swallowing anything it throws.
     *
     * @return true when the handler completed, false when it threw.
     */
    public fun run(what: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (error: Throwable) {
        failures++
        report(what, error)
        false
    }

    private fun report(what: String, error: Throwable) {
        val now = clock()
        val previous = lastReport.get()
        val due = previous == Long.MIN_VALUE || now - previous >= quietPeriodMillis
        if (due && lastReport.compareAndSet(previous, now)) {
            logger.log(
                Level.SEVERE,
                "[SculkPackets] A $what handler threw. The packet was left alone so the client falls back to " +
                    "vanilla behaviour; without this the player would have been kicked for a malformed packet. " +
                    "Further failures are logged at most once every ${quietPeriodMillis / 1000}s.",
                error,
            )
        }
    }
}
