package studio.sculk.holograms

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.adventure.parseMessage
import studio.sculk.annotation.SculkStable
import studio.sculk.packets.PacketDirection
import studio.sculk.packets.PacketKey
import studio.sculk.packets.SculkPacketService
import studio.sculk.packets.packetevents.PacketEventsPacket
import studio.sculk.scheduler.SculkScheduler
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders floating text as **virtual, packet-only** TextDisplay entities.
 *
 * No server-side entity is created, so holograms never tick, never appear in `/entities`, and never
 * burden the entity tracker. Spawn/metadata packets are sent only to players within
 * [HologramOptions.viewRangeBlocks], and text is re-sent only when it changes. A single reconcile
 * task (on the sync thread) handles enter/leave visibility for every hologram using a per-chunk
 * index, so cost scales with `players × nearby chunks` rather than `players × holograms`.
 *
 * Requires a PacketEvents-backed [SculkPacketService]. Build one from the platform once packets are
 * available:
 * ```kotlin
 * val holograms = (sculk.packetsResult as? SculkResult.Success)?.let {
 *     HologramService(it.value, sculk.scheduler)
 * }
 * ```
 *
 * All public methods are expected to be called from the main/region thread.
 *
 * Metadata indices below are the vanilla Minecraft 1.21 TextDisplay layout.
 */
@SculkStable
public class HologramService @JvmOverloads constructor(
    private val packetService: SculkPacketService,
    scheduler: SculkScheduler,
    reconcileIntervalTicks: Long = 10L,
) : SculkHandle {
    private val holograms: MutableMap<Int, HologramEntry> = ConcurrentHashMap()
    private val buckets: MutableMap<Long, MutableSet<HologramEntry>> = ConcurrentHashMap()
    private val allocator = EntityIdAllocator()

    @Volatile
    private var maxViewRangeBlocks: Double = 0.0

    private val handle: SculkHandle =
        scheduler.runSyncRepeating(reconcileIntervalTicks, reconcileIntervalTicks, ::reconcile)

    /** Creates a hologram at [location] showing [lines]. */
    @JvmOverloads
    public fun create(location: Location, lines: List<String>, options: HologramOptions = HologramOptions()): Hologram {
        val entry = HologramEntry(allocator.next(), UUID.randomUUID(), location.clone(), lines, options)
        holograms[entry.entityId] = entry
        index(entry)
        if (options.viewRangeBlocks > maxViewRangeBlocks) maxViewRangeBlocks = options.viewRangeBlocks
        return entry
    }

    /** The number of live holograms. */
    public val size: Int get() = holograms.size

    override fun close() {
        handle.close()
        holograms.values.toList().forEach { it.remove() }
        holograms.clear()
        buckets.clear()
    }

    // -----------------------------------------------------------------------
    // Reconcile (sync thread)
    // -----------------------------------------------------------------------

    private fun reconcile() {
        if (holograms.isEmpty()) return
        val players = Bukkit.getOnlinePlayers()
        val desired: MutableMap<HologramEntry, MutableSet<UUID>> = HashMap()
        if (players.isNotEmpty()) {
            val radius = HologramMath.chunkRadius(maxViewRangeBlocks)
            for (player in players) {
                val loc = player.location
                val cx = loc.blockX shr 4
                val cz = loc.blockZ shr 4
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        val bucket = buckets[chunkKey(cx + dx, cz + dz)] ?: continue
                        for (entry in bucket) {
                            val world = entry.location.world ?: continue
                            if (world.uid != player.world.uid) continue
                            if (entry.location.distanceSquared(loc) <= entry.viewRangeSq) {
                                desired.getOrPut(entry) { HashSet() }.add(player.uniqueId)
                            }
                        }
                    }
                }
            }
        }
        for (entry in holograms.values) {
            applyViewers(entry, desired[entry] ?: emptySet())
        }
    }

    private fun applyViewers(entry: HologramEntry, desired: Set<UUID>) {
        val added = HologramMath.toAdd(entry.viewers, desired)
        for (uuid in HologramMath.toRemove(entry.viewers, desired)) {
            Bukkit.getPlayer(uuid)?.let { send(it, destroyPacket(entry)) }
            entry.viewers.remove(uuid)
        }
        for (uuid in added) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            send(player, spawnPacket(entry))
            send(player, metadataPacket(entry))
            entry.viewers.add(uuid)
        }
        if (entry.dirty) {
            for (uuid in entry.viewers) {
                if (uuid in added) continue
                Bukkit.getPlayer(uuid)?.let { send(it, metadataPacket(entry)) }
            }
            entry.dirty = false
        }
    }

    private fun send(player: Player, packet: PacketEventsPacket) {
        packetService.send(player, packet)
    }

    // -----------------------------------------------------------------------
    // Per-chunk index
    // -----------------------------------------------------------------------

    private fun index(entry: HologramEntry) {
        val key = chunkKeyOf(entry.location)
        entry.chunkKey = key
        buckets.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(entry)
    }

    private fun unindex(entry: HologramEntry) {
        buckets[entry.chunkKey]?.let { set ->
            set.remove(entry)
            if (set.isEmpty()) buckets.remove(entry.chunkKey)
        }
    }

    private fun chunkKeyOf(location: Location): Long = chunkKey(location.blockX shr 4, location.blockZ shr 4)

    private fun chunkKey(chunkX: Int, chunkZ: Int): Long = (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xFFFFFFFFL)

    // -----------------------------------------------------------------------
    // Packet builders (vanilla 1.21 TextDisplay metadata indices)
    // -----------------------------------------------------------------------

    private fun spawnPacket(entry: HologramEntry): PacketEventsPacket {
        val loc = entry.location
        val wrapper =
            WrapperPlayServerSpawnEntity(
                entry.entityId,
                Optional.of(entry.entityUuid),
                EntityTypes.TEXT_DISPLAY,
                Vector3d(loc.x, loc.y + entry.options.yOffset, loc.z),
                0f,
                0f,
                0f,
                0,
                Optional.empty<Vector3d>(),
            )
        return PacketEventsPacket(wrapper, PacketDirection.Clientbound, SPAWN_KEY)
    }

    private fun metadataPacket(entry: HologramEntry): PacketEventsPacket {
        val data =
            mutableListOf<EntityData<*>>(
                EntityData(INDEX_TEXT, EntityDataTypes.ADV_COMPONENT, entry.component),
                EntityData(INDEX_BILLBOARD, EntityDataTypes.BYTE, entry.options.billboard.id),
                EntityData(INDEX_LINE_WIDTH, EntityDataTypes.INT, entry.options.lineWidthPixels),
                EntityData(INDEX_BACKGROUND, EntityDataTypes.INT, entry.options.backgroundArgb),
            )
        return PacketEventsPacket(WrapperPlayServerEntityMetadata(entry.entityId, data), PacketDirection.Clientbound, METADATA_KEY)
    }

    private fun destroyPacket(entry: HologramEntry): PacketEventsPacket =
        PacketEventsPacket(WrapperPlayServerDestroyEntities(entry.entityId), PacketDirection.Clientbound, DESTROY_KEY)

    // -----------------------------------------------------------------------

    private inner class HologramEntry(
        val entityId: Int,
        val entityUuid: UUID,
        var location: Location,
        lines: List<String>,
        val options: HologramOptions,
    ) : Hologram {
        val viewers: MutableSet<UUID> = HashSet()
        val viewRangeSq: Double = options.viewRangeBlocks * options.viewRangeBlocks
        var chunkKey: Long = 0

        @Volatile
        var dirty: Boolean = false

        @Volatile
        var component: Component = render(lines)

        override fun setLines(lines: List<String>) {
            component = render(lines)
            dirty = true
        }

        override fun teleport(location: Location) {
            unindex(this)
            this.location = location.clone()
            index(this)
            // Force a clean respawn at the new position on the next reconcile.
            viewers.toList().forEach { uuid -> Bukkit.getPlayer(uuid)?.let { send(it, destroyPacket(this)) } }
            viewers.clear()
        }

        override fun remove() {
            viewers.toList().forEach { uuid -> Bukkit.getPlayer(uuid)?.let { send(it, destroyPacket(this)) } }
            viewers.clear()
            holograms.remove(entityId)
            unindex(this)
        }
    }

    private companion object {
        // Vanilla Minecraft 1.21 TextDisplay metadata indices.
        const val INDEX_BILLBOARD = 15
        const val INDEX_TEXT = 23
        const val INDEX_LINE_WIDTH = 24
        const val INDEX_BACKGROUND = 25

        val SPAWN_KEY: PacketKey = PacketKey.of("spawn_entity")
        val METADATA_KEY: PacketKey = PacketKey.of("entity_metadata")
        val DESTROY_KEY: PacketKey = PacketKey.of("destroy_entities")

        fun render(lines: List<String>): Component {
            if (lines.isEmpty()) return Component.empty()
            var result = parseMessage(lines.first())
            for (i in 1 until lines.size) {
                result = result.append(Component.newline()).append(parseMessage(lines[i]))
            }
            return result
        }
    }
}
