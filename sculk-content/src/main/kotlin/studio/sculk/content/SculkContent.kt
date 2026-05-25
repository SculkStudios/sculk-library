package studio.sculk.content

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.packets.SculkPacketService

/**
 * High-level client-side content helpers backed by a [SculkPacketService].
 *
 * These APIs keep packet-powered UX behind descriptors and handles. Features that need
 * backend-specific entity packets return a clear failure until that backend implements them.
 */
public class SculkContent public constructor(
    private val packets: SculkPacketService,
) {
    public val clientBlocks: ClientBlockContent = ClientBlockContent(packets)
    public val holograms: HologramContent = HologramContent(packets)
    public val fakeEntities: FakeEntityContent = FakeEntityContent(packets)
    public val nametags: NametagContent = NametagContent(packets)
}

/** Creates a high-level content facade for this packet service. */
public val SculkPacketService.content: SculkContent
    get() = SculkContent(this)

public class ClientBlockContent internal constructor(
    private val packets: SculkPacketService,
) {
    public fun set(
        player: Player,
        location: Location,
        material: Material,
    ): SculkResult<Unit> = packets.clientBlocks.set(player, location, material)

    public fun reset(
        player: Player,
        location: Location,
    ): SculkResult<Unit> = packets.clientBlocks.reset(player, location)

    public fun preview(
        player: Player,
        location: Location,
        material: Material,
        durationTicks: Long,
    ): SculkResult<SculkHandle> = packets.clientBlocks.preview(player, location, material, durationTicks)
}

public class HologramContent internal constructor(
    private val packets: SculkPacketService,
) {
    public fun spawn(
        player: Player,
        location: Location,
        block: HologramDescriptorBuilder.() -> Unit,
    ): SculkResult<SculkHandle> {
        val descriptor = HologramDescriptorBuilder(location).apply(block).build()
        return packets.holograms.spawn(player, descriptor.location) {
            descriptor.lines.forEach(::line)
        }
    }
}

public data class HologramDescriptor(
    public val location: Location,
    public val lines: List<String>,
)

public class HologramDescriptorBuilder internal constructor(
    private val location: Location,
) {
    private val lines = mutableListOf<String>()

    public fun line(value: String) {
        lines += value
    }

    public fun build(): HologramDescriptor = HologramDescriptor(location, lines.toList())
}

public class FakeEntityContent internal constructor(
    private val packets: SculkPacketService,
) {
    public fun spawn(
        player: Player,
        block: FakeEntityDescriptorBuilder.() -> Unit,
    ): SculkResult<SculkHandle> {
        val descriptor = FakeEntityDescriptorBuilder().apply(block).build()
        return packets.fakeEntities.spawn(player) {
            type(descriptor.type)
            descriptor.location?.let(::location)
            if (descriptor.invisible) invisible()
            if (descriptor.marker) marker()
            descriptor.name?.let(::name)
        }
    }
}

public data class FakeEntityDescriptor(
    public val type: EntityType,
    public val location: Location?,
    public val invisible: Boolean,
    public val marker: Boolean,
    public val name: String?,
)

public class FakeEntityDescriptorBuilder {
    private var type: EntityType = EntityType.ARMOR_STAND
    private var location: Location? = null
    private var invisible: Boolean = false
    private var marker: Boolean = false
    private var name: String? = null

    public fun type(value: EntityType) {
        type = value
    }

    public fun location(value: Location) {
        location = value
    }

    public fun invisible() {
        invisible = true
    }

    public fun marker() {
        marker = true
    }

    public fun name(value: String) {
        name = value
    }

    public fun build(): FakeEntityDescriptor = FakeEntityDescriptor(type, location, invisible, marker, name)
}

public class NametagContent internal constructor(
    private val packets: SculkPacketService,
) {
    public fun update(
        player: Player,
        block: NametagDescriptorBuilder.() -> Unit,
    ): SculkResult<Unit> {
        val descriptor = NametagDescriptorBuilder().apply(block).build()
        return packets.nametags.update(player) {
            descriptor.prefix?.let(::prefix)
            descriptor.suffix?.let(::suffix)
        }
    }
}

public data class NametagDescriptor(
    public val prefix: String?,
    public val suffix: String?,
)

public class NametagDescriptorBuilder {
    private var prefix: String? = null
    private var suffix: String? = null

    public fun prefix(value: String) {
        prefix = value
    }

    public fun suffix(value: String) {
        suffix = value
    }

    public fun build(): NametagDescriptor = NametagDescriptor(prefix, suffix)
}
