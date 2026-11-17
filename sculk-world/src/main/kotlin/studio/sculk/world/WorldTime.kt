package studio.sculk.world

import net.kyori.adventure.key.Key
import org.bukkit.World
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.annotation.SculkInternal
import studio.sculk.execution.withGlobal
import studio.sculk.flatMap

/** World clocks and timelines on a server that has them (ADR-0028, ADR-0034). Not API. */
@SculkInternal
@OptIn(SculkExperimental::class)
public interface ClockAdapter {
    /** Every registered clock. */
    public fun clocks(): List<Key>

    /** Every registered timeline. */
    public fun timelines(): List<Timeline>

    /** The clock [world]'s dimension follows, or null when it follows none. */
    public fun defaultClock(world: World): Key?

    /** [world]'s state of [clock], or null when no clock is registered as [clock]. */
    public fun read(world: World, clock: Key): ClockState?

    /** Applies each non-null change to [world]'s [clock]; false when no clock is registered as [clock]. */
    public fun write(world: World, clock: Key, ticks: Long?, paused: Boolean?, rate: Float?): Boolean
}

/**
 * World clocks and the timelines that follow them, wherever the server's API has them: Paper 26.3
 * and later, and Folia and Canvas once their builds include them (their 26.3 builds forked Paper
 * before clocks). Everywhere else every call returns [SculkFailure.UnsupportedCapability]; those
 * servers keep a single day time, read through Bukkit's `World.getTime`.
 *
 * Every world keeps its own state for every clock, unless Paper's `time.affects-all-worlds` setting
 * makes them share one. Clocks are global state: every write runs on the global region (the main
 * thread on Paper, the global region thread on Folia and Canvas). Reads run on any thread and see
 * the clock as of the last tick that advanced it.
 */
@SculkExperimental
public object WorldTime {
    private const val ADAPTER = "studio.sculk.world.adapter.PaperClockAdapter"

    // Probed once: world clocks exist from Paper 26.3, and the adapter is compiled against the
    // newest anchor, so it is never linked where the API is missing.
    private val adapter: ClockAdapter? by lazy {
        if (!classExists("io.papermc.paper.world.WorldClock")) {
            null
        } else {
            Class.forName(ADAPTER, true, GameRuleNames::class.java.classLoader).getDeclaredConstructor().newInstance() as ClockAdapter
        }
    }

    /** Whether this server has world clocks and timelines. */
    public val isAvailable: Boolean get() = adapter != null

    /** Every clock the server registered, datapack clocks included. Any thread. */
    public fun clocks(): SculkResult<List<ClockKey>> = available().flatMap { SculkResult.success(it.clocks().map(::ClockKey)) }

    /** Every timeline the server registered. Any thread. */
    public fun timelines(): SculkResult<List<Timeline>> = available().flatMap { SculkResult.success(it.timelines()) }

    /** The timeline registered as [key], or [SculkFailure.InvalidInput] when there is none. Any thread. */
    public fun timeline(key: TimelineKey): SculkResult<Timeline> = timelines().flatMap { all ->
        all.firstOrNull { it.key == key }?.let { SculkResult.success(it) }
            ?: SculkFailure.InvalidInput("timeline", "a registered timeline", key.toString())
    }

    /** The clock [world]'s dimension follows, null for one that follows none (the Nether). Any thread. */
    public fun defaultClock(world: World): SculkResult<ClockKey?> =
        available().flatMap { SculkResult.success(it.defaultClock(world)?.let(::ClockKey)) }

    /** [world]'s state of [clock]. Any thread. [SculkFailure.InvalidInput] when the server has no such clock. */
    public fun clock(world: World, clock: ClockKey): SculkResult<ClockState> = available().flatMap { clocks ->
        clocks.read(world, clock.key)?.let { SculkResult.success(it) } ?: unknown(clock)
    }

    /** Where [timeline] stands in [world], read from its clock. Any thread. */
    public fun position(world: World, timeline: TimelineKey): SculkResult<TimelinePosition> = this.timeline(timeline).flatMap { found ->
        this.clock(world, found.clock).flatMap { state -> SculkResult.success(found.at(state.ticks)) }
    }

    /**
     * Changes [world]'s [clock] from any thread of a Sculk scope, on the global region, and returns
     * its state afterwards. Runs inline when the caller is already on the global region. Fails as
     * [updateNow] does.
     */
    public suspend fun update(
        world: World,
        clock: ClockKey,
        ticks: Long? = null,
        paused: Boolean? = null,
        rate: Float? = null,
    ): SculkResult<ClockState> = withGlobal { updateNow(world, clock, ticks, paused, rate) }

    /**
     * Sets each of [ticks], [paused] and [rate] that is not null on [world]'s [clock] and returns its
     * state afterwards. The caller must be on the global region; anywhere else this throws
     * [IllegalStateException]. Setting [ticks] fires `ClockTimeSkipEvent`, whose listeners may
     * cancel or change the jump, so the state returned is what the clock reads, not what was asked.
     *
     * [SculkFailure.InvalidInput] for negative [ticks], a [rate] that is not positive and finite, or
     * a clock the server does not have; [SculkFailure.UnsupportedCapability] before Paper 26.3.
     * Nothing changes when it fails.
     */
    public fun updateNow(
        world: World,
        clock: ClockKey,
        ticks: Long? = null,
        paused: Boolean? = null,
        rate: Float? = null,
    ): SculkResult<ClockState> {
        val clocks = adapter ?: return unsupported()
        if (ticks != null && ticks < 0) return SculkFailure.InvalidInput("clock $clock", "zero or more ticks", ticks.toString())
        if (rate != null && !(rate.isFinite() && rate > 0f)) {
            return SculkFailure.InvalidInput("clock $clock", "a positive, finite rate", rate.toString())
        }
        requireGlobal("Changing a world clock")
        if (!clocks.write(world, clock.key, ticks, paused, rate)) return unknown(clock)
        return this.clock(world, clock)
    }

    private fun available(): SculkResult<ClockAdapter> = adapter?.let { SculkResult.success(it) } ?: unsupported()

    private fun unsupported(): SculkFailure = SculkFailure.UnsupportedCapability(
        "World clocks and timelines",
        "they were added in Paper 26.3, and this server's API does not have them",
        hint = "World.getTime and World.setTime keep working on every version.",
    )

    private fun unknown(clock: ClockKey): SculkFailure = SculkFailure.InvalidInput("world clock", "a registered clock", clock.toString())
}
