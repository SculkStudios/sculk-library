package studio.sculk.placeholder

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkScope
import studio.sculk.time.SculkClock
import kotlin.time.Duration.Companion.seconds

/** How a placeholder computes a value: inline on the rendering thread, or loaded in [ExecutionContext.Async]. */
internal sealed interface ValueSource<C : PlaceholderContext, T : Any> {
    class Sync<C : PlaceholderContext, T : Any>(val resolve: C.() -> T?) : ValueSource<C, T>

    class Async<C : PlaceholderContext, T : Any>(val resolve: suspend C.() -> T?, val initial: T?) : ValueSource<C, T>
}

/** Where one value is kept: whose it is, for whom, and with which arguments. */
internal data class Slot(val subject: Any?, val target: Any?, val arguments: List<String>)

/** An audience's cache identity: its UUID pointer, or the audience itself when it has none, as the console does. */
internal fun identity(audience: Audience): Any = audience.getOrDefault(Identity.UUID, null) ?: audience

/**
 * The values of one placeholder, bounded to [capacity] slots, least recently used first out.
 *
 * Every read and write holds [lock] only to look up or replace an entry; resolvers always run
 * outside it, so a slow or re-entrant resolver never blocks another region's render.
 */
internal class ValueStore<C : PlaceholderContext, T : Any>(
    private val refresh: Refresh,
    private val source: ValueSource<C, T>,
    private val capacity: Int,
    private val clock: SculkClock,
    private val scope: SculkScope,
    private val changed: (Slot) -> Unit,
) {
    /** [sequence] is taken when the computation started, so an invalidation during it leaves the result stale. */
    private class Entry<T>(
        val value: T?,
        val hasValue: Boolean,
        val sequence: Long,
        val startedAt: Long,
        val lifetime: Long,
        val invalidatedAt: Long,
    ) {
        fun invalidated(at: Long): Entry<T> = Entry(value, hasValue, sequence, startedAt, lifetime, at)
    }

    private val lock = Any()
    private val entries = object : LinkedHashMap<Slot, Entry<T>>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Slot, Entry<T>>?): Boolean = size > capacity
    }
    private val loading = HashSet<Slot>()
    private var sequence = 0L
    private var everyoneInvalidatedAt = 0L
    private var closed = false

    private val lifetime: Long = when (refresh) {
        Refresh.Live -> 0
        is Refresh.Every -> refresh.interval.inWholeNanoseconds
        Refresh.UntilInvalidated -> Long.MAX_VALUE
    }

    val size: Int get() = synchronized(lock) { entries.size }

    fun read(slot: Slot, context: C): T? = when (source) {
        is ValueSource.Sync -> if (refresh == Refresh.Live) source.resolve(context) else readSync(slot, context, source)
        is ValueSource.Async -> readAsync(slot, context, source)
    }

    private fun readSync(slot: Slot, context: C, source: ValueSource.Sync<C, T>): T? {
        val started = synchronized(lock) {
            entries[slot]?.takeIf(::isFresh)?.let { return it.value }
            ++sequence
        }
        val startedAt = clock.monotonicNanos()
        val value = source.resolve(context)
        store(slot, value, started, startedAt)
        return value
    }

    private fun readAsync(slot: Slot, context: C, source: ValueSource.Async<C, T>): T? {
        val (shown, started) = synchronized(lock) {
            val entry = entries[slot]
            val shown = if (entry?.hasValue == true) entry.value else source.initial
            if (closed || slot in loading || (entry != null && isFresh(entry))) return shown
            loading += slot
            shown to ++sequence
        }
        load(slot, context, source, started)
        return shown
    }

    private fun load(slot: Slot, context: C, source: ValueSource.Async<C, T>, started: Long) {
        val startedAt = clock.monotonicNanos()
        try {
            scope.launch(scope.execution.dispatcher(ExecutionContext.Async)) {
                val value = try {
                    source.resolve(context)
                } catch (failure: Throwable) {
                    fail(slot, started, startedAt)
                    throw failure
                }
                store(slot, value, started, startedAt)
            }
        } catch (rejected: IllegalStateException) {
            synchronized(lock) { loading -= slot }
            // The scope closed between the render's check and the launch; nothing loads after shutdown.
            if (!scope.isClosed) throw rejected
        }
    }

    private fun store(slot: Slot, value: T?, started: Long, startedAt: Long) {
        val signal = synchronized(lock) {
            if (closed) return
            loading -= slot
            val previous = entries[slot]
            // Two renders on different regions may compute one value at once; the later computation wins.
            if (previous != null && previous.sequence > started) return
            entries[slot] = Entry(value, true, started, startedAt, lifetime, previous?.invalidatedAt ?: 0)
            when {
                previous == null || !previous.hasValue -> source is ValueSource.Async

                previous.value == value -> false

                // A synchronous value recomputed after an invalidation was signalled by the invalidation itself.
                else -> source is ValueSource.Async || previous.sequence > maxOf(previous.invalidatedAt, everyoneInvalidatedAt)
            }
        }
        if (signal) changed(slot)
    }

    /** Keeps the last value, and holds the next attempt back for [FAILURE_RETRY] unless invalidated sooner. */
    private fun fail(slot: Slot, started: Long, startedAt: Long) {
        synchronized(lock) {
            if (closed) return
            loading -= slot
            val previous = entries[slot]
            entries[slot] = Entry(
                previous?.value,
                previous?.hasValue ?: false,
                started,
                startedAt,
                FAILURE_RETRY.inWholeNanoseconds,
                previous?.invalidatedAt ?: 0,
            )
        }
    }

    /** Marks every slot matching [matches] stale, or every slot when it is null. Stale values stay shown until replaced. */
    fun invalidate(matches: ((Slot) -> Boolean)?) {
        synchronized(lock) {
            val at = ++sequence
            if (matches == null) {
                everyoneInvalidatedAt = at
                return
            }
            entries.replaceAll { slot, entry -> if (matches(slot)) entry.invalidated(at) else entry }
            loading.filter { matches(it) && it !in entries }.forEach { entries[it] = Entry(null, false, 0, 0, 0, at) }
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            entries.clear()
            loading.clear()
        }
    }

    private fun isFresh(entry: Entry<T>): Boolean =
        entry.sequence > maxOf(entry.invalidatedAt, everyoneInvalidatedAt) && clock.monotonicNanos() - entry.startedAt < entry.lifetime

    companion object {
        val FAILURE_RETRY = 5.seconds
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
