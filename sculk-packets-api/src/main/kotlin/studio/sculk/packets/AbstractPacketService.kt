package studio.sculk.packets

import org.bukkit.entity.Player
import studio.sculk.SculkResult
import studio.sculk.scheduler.SculkScheduler

/**
 * Base implementation shared by lightweight backend adapters.
 */
public abstract class AbstractPacketService(
    final override val backend: PacketBackend,
    protected val scheduler: SculkScheduler,
) : SculkPacketService {
    final override val clientBlocks: ClientBlockService = ClientBlockService(scheduler)
    final override val debug: PacketDebugService = PacketDebugService(this, scheduler)

    override fun send(
        player: Player,
        packet: SculkPacket,
    ): SculkResult<Unit> = SculkResult.failure("${backend.name} packet sending is not available for packet type ${packet.type}.")

    override fun close() {
        // Backend adapters override when they own registered listeners.
    }
}
