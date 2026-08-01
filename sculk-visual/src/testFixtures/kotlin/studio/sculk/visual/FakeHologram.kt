package studio.sculk.visual

import org.bukkit.Location
import studio.sculk.annotation.SculkStable

/**
 * A [Hologram] that records instead of sending packets.
 *
 * For testing the code that drives a hologram — a countdown, a boss health display — without a
 * server or a packet backend. What it records is what a viewer would have been sent.
 *
 * ```kotlin
 * val hologram = FakeHologram()
 * val timer = RoundTimer(hologram)
 *
 * timer.tick(seconds = 30)
 *
 * assertEquals(listOf("<gold>30s"), hologram.lines)
 * ```
 */
@SculkStable
public class FakeHologram(location: Location? = null) : Hologram {
    /** The lines as last set. */
    public var lines: List<String> = emptyList()
        private set

    /** Where it was last moved to, or where it started. */
    public var location: Location? = location
        private set

    /** Every value [setLines] was called with, oldest first — for asserting what changed and when. */
    public val lineHistory: MutableList<List<String>> = mutableListOf()

    /** How many times it was moved. A hologram that teleports every tick is a bug worth catching. */
    public var teleports: Int = 0
        private set

    public var removed: Boolean = false
        private set

    override fun setLines(lines: List<String>) {
        check(!removed) { "setLines was called on a hologram that had already been removed." }
        this.lines = lines.toList()
        lineHistory += lines.toList()
    }

    override fun teleport(location: Location) {
        check(!removed) { "teleport was called on a hologram that had already been removed." }
        this.location = location
        teleports++
    }

    override fun remove() {
        removed = true
    }
}
