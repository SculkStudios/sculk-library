package studio.sculk.world

import net.kyori.adventure.key.Key
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental

/**
 * A world clock by its key in the `world_clock` registry (Minecraft 26.3): a tick counter that runs
 * at its own rate and can be paused. Each dimension follows one clock, or none; every world keeps
 * its own state for every registered clock. Whether the server has [key] is checked when it is used.
 */
@SculkExperimental
public class ClockKey(public val key: Key) {
    override fun equals(other: Any?): Boolean = other is ClockKey && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = key.asString()

    @SculkExperimental
    public companion object {
        /** The overworld's clock, which the day, moon and villager timelines follow. */
        public val OVERWORLD: ClockKey = ClockKey(Key.key(Key.MINECRAFT_NAMESPACE, "overworld"))

        /** The End's clock. The Nether follows no clock. */
        public val THE_END: ClockKey = ClockKey(Key.key(Key.MINECRAFT_NAMESPACE, "the_end"))

        /** The clock [text] names, `minecraft:` when it has no namespace, or [SculkFailure.InvalidInput] for a malformed key. */
        public fun parse(text: String): SculkResult<ClockKey> = keyOrNull(text.trim())?.let { SculkResult.success(ClockKey(it)) }
            ?: SculkFailure.InvalidInput("world clock", "a key such as minecraft:overworld", "'$text'")
    }
}

/** A timeline by its key in the `timeline` registry (Minecraft 26.3); checked against the server when used. */
@SculkExperimental
public class TimelineKey(public val key: Key) {
    override fun equals(other: Any?): Boolean = other is TimelineKey && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = key.asString()

    @SculkExperimental
    public companion object {
        /** The day and night cycle: 24000 ticks of the overworld clock. */
        public val DAY: TimelineKey = TimelineKey(Key.key(Key.MINECRAFT_NAMESPACE, "day"))

        /** The first days of a world, when no pillager patrols spawn. It has no period. */
        public val EARLY_GAME: TimelineKey = TimelineKey(Key.key(Key.MINECRAFT_NAMESPACE, "early_game"))

        /** The moon's eight phases: 192000 ticks of the overworld clock, one phase per 24000. */
        public val MOON: TimelineKey = TimelineKey(Key.key(Key.MINECRAFT_NAMESPACE, "moon"))

        /** When villagers work, meet and sleep: 24000 ticks of the overworld clock. */
        public val VILLAGER_SCHEDULE: TimelineKey = TimelineKey(Key.key(Key.MINECRAFT_NAMESPACE, "villager_schedule"))

        /** The timeline [text] names, `minecraft:` when it has no namespace, or [SculkFailure.InvalidInput] for a malformed key. */
        public fun parse(text: String): SculkResult<TimelineKey> = keyOrNull(text.trim())?.let { SculkResult.success(TimelineKey(it)) }
            ?: SculkFailure.InvalidInput("timeline", "a key such as minecraft:day", "'$text'")
    }
}

/**
 * One world's state of [clock] when it was read: [ticks] counted since the world began, whether it
 * is [paused], and the [rate] it advances at, in clock ticks per server tick. A snapshot; it does not
 * follow the clock.
 */
@SculkExperimental
public class ClockState(public val clock: ClockKey, public val ticks: Long, public val paused: Boolean, public val rate: Float) {
    override fun equals(other: Any?): Boolean =
        other is ClockState && other.clock == clock && other.ticks == ticks && other.paused == paused && other.rate == rate

    override fun hashCode(): Int = ((clock.hashCode() * 31 + ticks.hashCode()) * 31 + paused.hashCode()) * 31 + rate.hashCode()

    override fun toString(): String = "ClockState($clock, ticks=$ticks, paused=$paused, rate=$rate)"
}

/**
 * A timeline as the server registered it: it follows [clock] and repeats every [periodTicks], or
 * never when that is null. Timelines drive what changes with time of day, such as sky colour, mob
 * spawning and villager schedules.
 */
@SculkExperimental
public class Timeline(public val key: TimelineKey, public val clock: ClockKey, public val periodTicks: Int?) {
    init {
        require(periodTicks == null || periodTicks > 0) { "A timeline's period is positive, but $key has $periodTicks." }
    }

    /** Where a clock reading of [clockTicks] falls on this timeline. */
    public fun at(clockTicks: Long): TimelinePosition = TimelinePosition(this, clockTicks)

    override fun equals(other: Any?): Boolean =
        other is Timeline && other.key == key && other.clock == clock && other.periodTicks == periodTicks

    override fun hashCode(): Int = (key.hashCode() * 31 + clock.hashCode()) * 31 + (periodTicks ?: 0)

    override fun toString(): String = "Timeline($key, clock=$clock, period=${periodTicks ?: "none"})"
}

/**
 * Where [timeline] stands when its clock reads [clockTicks]: [tick] into the current period and
 * [cycle] periods completed. For [TimelineKey.DAY], the time of day and the day number; a timeline
 * without a period has one endless cycle, so [tick] is [clockTicks] and [cycle] is 0.
 */
@SculkExperimental
public class TimelinePosition(public val timeline: Timeline, public val clockTicks: Long) {
    public val tick: Long get() = timeline.periodTicks?.let { Math.floorMod(clockTicks, it.toLong()) } ?: clockTicks

    public val cycle: Long get() = timeline.periodTicks?.let { Math.floorDiv(clockTicks, it.toLong()) } ?: 0L

    override fun equals(other: Any?): Boolean = other is TimelinePosition && other.timeline == timeline && other.clockTicks == clockTicks

    override fun hashCode(): Int = timeline.hashCode() * 31 + clockTicks.hashCode()

    override fun toString(): String = "TimelinePosition(${timeline.key}, tick=$tick, cycle=$cycle)"
}
