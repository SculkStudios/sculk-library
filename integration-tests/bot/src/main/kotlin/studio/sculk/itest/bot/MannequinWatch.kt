package studio.sculk.itest.bot

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.cloudburstmc.math.vector.Vector3d
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.ResolvableProfile
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket
import java.util.Optional

/**
 * The mannequins the client knows of, for the NPC suite (ADR-0033). It reports:
 *
 * - `MANNEQUIN id uuid headYaw` when one is added;
 * - `MANNEQUINNAME id text`, `MANNEQUINDESC id text` (both plain, empty when absent) and
 *   `MANNEQUINSKIN id textures` (the profile's `textures` property value, empty without one) as its
 *   data arrives;
 * - `MANNEQUINHEAD id yaw` when its head turns;
 * - `MANNEQUINGONE id` when it is removed.
 *
 * It only watches, so other watchers still see every packet. Other entities are ignored.
 */
internal class MannequinWatch(private val emit: (List<String>) -> Unit) {
    private val plain = PlainTextComponentSerializer.plainText()
    private val known = HashSet<Int>()

    fun received(packet: Packet) {
        when (packet) {
            is ClientboundAddEntityPacket -> if (packet.type == EntityType.MANNEQUIN) {
                known += packet.entityId
                emit(listOf("MANNEQUIN", "${packet.entityId}", "${packet.uuid}", "${packet.headYaw}"))
            }
            is ClientboundSetEntityDataPacket -> if (packet.entityId in known) packet.metadata.forEach { data(packet.entityId, it) }
            is ClientboundRotateHeadPacket -> if (packet.entityId in known) emit(listOf("MANNEQUINHEAD", "${packet.entityId}", "${packet.headYaw}"))
            is ClientboundRemoveEntitiesPacket -> packet.entityIds.forEach { if (known.remove(it)) emit(listOf("MANNEQUINGONE", "$it")) }
        }
    }

    // Index 2 is every entity's custom name; the mannequin's only other optional text is its description.
    private fun data(id: Int, entry: EntityMetadata<*, *>) {
        when (entry.type) {
            MetadataTypes.OPTIONAL_COMPONENT -> {
                val text = (entry.value as Optional<*>).map { plain.serialize(it as Component) }.orElse("")
                emit(listOf(if (entry.id == 2) "MANNEQUINNAME" else "MANNEQUINDESC", "$id", text))
            }
            MetadataTypes.RESOLVABLE_PROFILE -> {
                val textures = (entry.value as ResolvableProfile).profile.getProperty("textures")?.value.orEmpty()
                emit(listOf("MANNEQUINSKIN", "$id", textures))
            }
            else -> {}
        }
    }

    companion object {
        /** A left click on entity [id]. */
        fun attack(id: Int): Packet = ServerboundAttackPacket(id)

        /** A right click on entity [id] with the main hand, aimed at the middle of its body. */
        fun interact(id: Int): Packet = ServerboundInteractPacket(id, Hand.MAIN_HAND, Vector3d.from(0.0, 1.0, 0.0), false)
    }
}
