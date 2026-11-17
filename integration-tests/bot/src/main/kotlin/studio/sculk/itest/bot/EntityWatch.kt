package studio.sculk.itest.bot

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.cloudburstmc.math.imaginary.Quaternionf
import org.cloudburstmc.math.vector.Vector3f
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket

/**
 * The display entities the client knows of, for the scenes suite (ADR-0032). It reports:
 *
 * - `DISPLAY id type x y z` when a text, item or block display is added;
 * - `DISPLAYDATA id entry...` for each metadata packet about one, each entry `index=kind:value` with
 *   kind `text` (plain), `item` (item id), `block` (state id), `vec` (`x,y,z`), `quat` (`x,y,z,w`),
 *   `int`, `float` or `byte`;
 * - `DISPLAYMOVE id x y z` where it now stands, after any teleport or move;
 * - `DISPLAYGONE id` when it is removed.
 *
 * Other entities are ignored.
 */
internal class EntityWatch(private val emit: (List<String>) -> Unit) {
    private val plain = PlainTextComponentSerializer.plainText()
    private val positions = HashMap<Int, DoubleArray>()

    /** Reports [packet] if it concerns a display entity; false for any packet this does not handle. */
    fun received(packet: Packet): Boolean {
        when (packet) {
            is ClientboundAddEntityPacket -> added(packet)
            is ClientboundSetEntityDataPacket -> data(packet)
            is ClientboundTeleportEntityPacket -> teleported(packet)
            is ClientboundEntityPositionSyncPacket -> packet.endPosition?.let { moveTo(packet.id, it.x, it.y, it.z) }
            is ClientboundMoveEntityPosPacket -> moveBy(packet.entityId, packet.moveX, packet.moveY, packet.moveZ)
            is ClientboundMoveEntityPosRotPacket -> moveBy(packet.entityId, packet.moveX, packet.moveY, packet.moveZ)
            is ClientboundRemoveEntitiesPacket -> packet.entityIds.forEach { if (positions.remove(it) != null) emit(listOf("DISPLAYGONE", "$it")) }
            else -> return false
        }
        return true
    }

    private fun added(packet: ClientboundAddEntityPacket) {
        if (packet.type !in DISPLAYS) return
        positions[packet.entityId] = doubleArrayOf(packet.x, packet.y, packet.z)
        emit(listOf("DISPLAY", "${packet.entityId}", packet.type.name, "${packet.x}", "${packet.y}", "${packet.z}"))
    }

    private fun data(packet: ClientboundSetEntityDataPacket) {
        if (packet.entityId !in positions) return
        emit(listOf("DISPLAYDATA", "${packet.entityId}") + packet.metadata.mapNotNull(::entry))
    }

    private fun teleported(packet: ClientboundTeleportEntityPacket) {
        val at = positions[packet.id] ?: return
        val relative = packet.relatives.toSet()
        val to = packet.position
        moveTo(
            packet.id,
            if (PositionElement.X in relative) at[0] + to.x else to.x,
            if (PositionElement.Y in relative) at[1] + to.y else to.y,
            if (PositionElement.Z in relative) at[2] + to.z else to.z,
        )
    }

    private fun moveBy(id: Int, dx: Double, dy: Double, dz: Double) {
        val at = positions[id] ?: return
        moveTo(id, at[0] + dx, at[1] + dy, at[2] + dz)
    }

    private fun moveTo(id: Int, x: Double, y: Double, z: Double) {
        if (id !in positions) return
        positions[id] = doubleArrayOf(x, y, z)
        emit(listOf("DISPLAYMOVE", "$id", "$x", "$y", "$z"))
    }

    private fun entry(entry: EntityMetadata<*, *>): String? {
        val value = entry.value
        val shown = when (entry.type) {
            MetadataTypes.COMPONENT -> "text:" + plain.serialize(value as Component)
            MetadataTypes.ITEM_STACK -> "item:" + ((value as? ItemStack)?.id ?: "")
            MetadataTypes.BLOCK_STATE -> "block:$value"
            MetadataTypes.VECTOR3 -> (value as Vector3f).let { "vec:${it.x},${it.y},${it.z}" }
            MetadataTypes.QUATERNION -> (value as Quaternionf).let { "quat:${it.x},${it.y},${it.z},${it.w}" }
            MetadataTypes.INT -> "int:$value"
            MetadataTypes.FLOAT -> "float:$value"
            MetadataTypes.BYTE -> "byte:$value"
            else -> return null
        }
        return "${entry.id}=$shown"
    }

    private companion object {
        val DISPLAYS = setOf(EntityType.TEXT_DISPLAY, EntityType.ITEM_DISPLAY, EntityType.BLOCK_DISPLAY)
    }
}
