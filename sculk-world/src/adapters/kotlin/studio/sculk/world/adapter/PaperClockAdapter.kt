package studio.sculk.world.adapter

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import org.bukkit.World
import studio.sculk.annotation.SculkExperimental
import studio.sculk.world.ClockAdapter
import studio.sculk.world.ClockKey
import studio.sculk.world.ClockState
import studio.sculk.world.Timeline
import studio.sculk.world.TimelineKey

/** Paper 26.3 and later: world clocks and timelines through their registries. Instantiated by name only. */
@OptIn(SculkExperimental::class)
internal class PaperClockAdapter : ClockAdapter {
    private val clockRegistry get() = RegistryAccess.registryAccess().getRegistry(RegistryKey.WORLD_CLOCK)

    private val timelineRegistry get() = RegistryAccess.registryAccess().getRegistry(RegistryKey.TIMELINE)

    override fun clocks(): List<Key> = clockRegistry.map { plain(it.key()) }

    override fun timelines(): List<Timeline> = timelineRegistry.map { timeline ->
        val period = timeline.periodTicks()
        Timeline(TimelineKey(plain(timeline.key())), ClockKey(plain(timeline.clock().key())), if (period.isPresent) period.asInt else null)
    }

    override fun defaultClock(world: World): Key? = world.defaultClock?.let { plain(it.key()) }

    override fun read(world: World, clock: Key): ClockState? {
        val handle = clockRegistry.get(clock) ?: return null
        return ClockState(ClockKey(clock), world.getFullTime(handle), world.isClockPaused(handle), world.getClockRate(handle))
    }

    override fun write(world: World, clock: Key, ticks: Long?, paused: Boolean?, rate: Float?): Boolean {
        val handle = clockRegistry.get(clock) ?: return false
        rate?.let { world.setClockRate(handle, it) }
        paused?.let { world.setClockPaused(handle, it) }
        ticks?.let { world.setFullTime(handle, it) }
        return true
    }

    // NamespacedKey and Adventure's own Key implementation do not promise to equal each other.
    private fun plain(key: Key): Key = Key.key(key.namespace(), key.value())
}
