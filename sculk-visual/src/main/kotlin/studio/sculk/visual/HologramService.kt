package studio.sculk.visual

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.packets.TextDisplayStyle
import studio.sculk.packets.VirtualEntityService
import studio.sculk.scheduler.SculkScheduler
import studio.sculk.text.SculkMessages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Floating text that costs the server no entities: nothing ticks, nothing persists, and a crash
 * cannot orphan it.
 *
 * Holograms are bucketed by chunk, so visibility costs `players x nearby chunks` rather than
 * `players x holograms`, and text is re-sent only when it changed.
 *
 * All public methods expect the main or owning region thread.
 *
 * See [docs.sculk.studio/visual/holograms](https://docs.sculk.studio/visual/holograms/).
 */
@SculkStable
public class HologramService
@SculkInternal
constructor(
    private val entities: VirtualEntityService,
    private val messages: SculkMessages,
    scheduler: SculkScheduler,
    reconcileIntervalTicks: Long = 10L,
    // Injected rather than called statically so the reconcile loop can be driven with no server.
    private val onlinePlayers: () -> Collection<Player> = { org.bukkit.Bukkit.getOnlinePlayers() },
    private val viewerOf: (UUID) -> Player? = { org.bukkit.Bukkit.getPlayer(it) },
) : SculkHandle {
    private val holograms = ConcurrentHashMap<Int, Entry>()
    private val buckets = ConcurrentHashMap<Long, MutableSet<Int>>()
    private val task: SculkHandle =
        scheduler.runSyncRepeating(reconcileIntervalTicks, reconcileIntervalTicks, ::reconcile)

    /** True when the packet backend can actually show these. */
    @SculkStable
    public val available: Boolean get() = entities.available

    @SculkStable
    public fun create(location: Location, lines: List<String>, options: HologramOptions = HologramOptions()): Hologram {
        val entry = Entry(
            id = entities.reserveEntityId(),
            location = location.clone().add(0.0, options.yOffset, 0.0),
            lines = lines,
            options = options,
        )
        holograms[entry.id] = entry
        index(entry)
        return Handle(entry)
    }

    /** How many holograms exist. Exposed so a test can assert nothing leaks. */
    @SculkInternal
    public val count: Int get() = holograms.size

    override fun close() {
        task.close()
        for (entry in holograms.values) {
            for (uuid in entry.viewers) {
                viewerOf(uuid)?.let { entities.despawn(it, listOf(entry.id)) }
            }
        }
        holograms.clear()
        buckets.clear()
    }

    private fun reconcile() {
        if (!entities.available) return
        val wanted = HashMap<Int, MutableSet<UUID>>()

        for (player in onlinePlayers()) {
            val chunkX = player.location.blockX shr 4
            val chunkZ = player.location.blockZ shr 4
            val radius = HologramMath.chunkRadius(maxViewRange())

            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val bucket = buckets[chunkKey(chunkX + dx, chunkZ + dz)] ?: continue
                    for (id in bucket) {
                        val entry = holograms[id] ?: continue
                        if (entry.location.world != player.world) continue
                        if (entry.location.distanceSquared(player.location) > entry.options.viewRangeBlocks.squared()) continue
                        wanted.getOrPut(id) { HashSet() } += player.uniqueId
                    }
                }
            }
        }

        for (entry in holograms.values) {
            applyViewers(entry, wanted[entry.id].orEmpty())
        }
    }

    private fun applyViewers(entry: Entry, desired: Set<UUID>) {
        for (uuid in HologramMath.toRemove(entry.viewers, desired)) {
            viewerOf(uuid)?.let { entities.despawn(it, listOf(entry.id)) }
            entry.viewers.remove(uuid)
        }

        for (uuid in HologramMath.toAdd(entry.viewers, desired)) {
            val player = viewerOf(uuid) ?: continue
            entities.spawnTextDisplay(player, entry.id, entry.location, entry.render(), entry.style())
            entry.viewers.add(uuid)
        }

        // Only when something changed: a metadata packet costs the same whether or not the text
        // differs, and a hologram nobody edited should be free.
        if (entry.dirty) {
            val text = entry.render()
            val style = entry.style()
            for (uuid in entry.viewers) {
                viewerOf(uuid)?.let { entities.updateTextDisplay(it, entry.id, text, style) }
            }
            entry.dirty = false
        }

        if (entry.moved) {
            for (uuid in entry.viewers) {
                viewerOf(uuid)?.let { entities.teleport(it, entry.id, entry.location) }
            }
            entry.moved = false
        }
    }

    private fun index(entry: Entry) {
        entry.chunkKey = chunkKeyOf(entry.location)
        buckets.getOrPut(entry.chunkKey) { ConcurrentHashMap.newKeySet() } += entry.id
    }

    private fun unindex(entry: Entry) {
        buckets[entry.chunkKey]?.let { bucket ->
            bucket -= entry.id
            if (bucket.isEmpty()) buckets.remove(entry.chunkKey)
        }
    }

    private fun maxViewRange(): Double = holograms.values.maxOfOrNull { it.options.viewRangeBlocks } ?: 0.0

    private fun chunkKeyOf(location: Location): Long = chunkKey(location.blockX shr 4, location.blockZ shr 4)

    private fun chunkKey(chunkX: Int, chunkZ: Int): Long = (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xFFFFFFFFL)

    private fun Double.squared() = this * this

    private inner class Handle(private val entry: Entry) : Hologram {
        override fun setLines(lines: List<String>) {
            if (entry.lines == lines) return
            entry.lines = lines
            entry.rendered = null
            entry.dirty = true
        }

        override fun teleport(location: Location) {
            unindex(entry)
            entry.location = location.clone().add(0.0, entry.options.yOffset, 0.0)
            entry.moved = true
            index(entry)
        }

        override fun remove() {
            for (uuid in entry.viewers) {
                viewerOf(uuid)?.let { entities.despawn(it, listOf(entry.id)) }
            }
            entry.viewers.clear()
            unindex(entry)
            holograms.remove(entry.id)
        }
    }

    private inner class Entry(val id: Int, var location: Location, var lines: List<String>, val options: HologramOptions) {
        val viewers: MutableSet<UUID> = HashSet()
        var chunkKey: Long = 0
        var dirty: Boolean = false
        var moved: Boolean = false
        var rendered: Component? = null

        fun render(): Component = rendered ?: buildText().also { rendered = it }

        fun style(): TextDisplayStyle = TextDisplayStyle(
            billboard = options.billboard,
            scale = options.scale,
            backgroundArgb = options.backgroundArgb,
            shadowed = options.shadowed,
            seeThrough = options.seeThrough,
            lineWidth = options.lineWidthPixels,
        )

        private fun buildText(): Component {
            if (lines.isEmpty()) return Component.empty()
            var text = messages.render(lines.first())
            for (line in lines.drop(1)) {
                text = text.append(Component.newline()).append(messages.render(line))
            }
            return text
        }
    }
}
