package studio.sculk.packets

import org.bukkit.entity.Player
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler

/**
 * Base implementation shared by lightweight backend adapters.
 */
@SculkStable
public abstract class AbstractPacketService(final override val backend: PacketBackend, protected val scheduler: SculkScheduler) :
    SculkPacketService {
    /**
     * Backend capabilities behind [clientBlocks] that plain block changes cannot express.
     * Adapters that can read dig packets override this; the rest inherit a service whose
     * [ClientBlockService.onDig] and [ClientBlockService.acknowledge] fail cleanly.
     *
     * Resolved lazily so overrides may reference subclass state.
     */
    protected open fun clientBlockBackend(): ClientBlockBackend? = null

    final override val clientBlocks: ClientBlockService by lazy { ClientBlockService(scheduler, clientBlockBackend()) }

    /**
     * The backend's virtual entity support, if it has any.
     *
     * Defaults to unavailable so a backend that has not implemented it still compiles and still
     * degrades by name -- ProtocolLib is in exactly that position, because its entity-metadata
     * serialisation is where version fragility lives.
     */
    protected open fun virtualEntityService(): VirtualEntityService = UnavailableVirtualEntityService

    final override val virtualEntities: VirtualEntityService by lazy { virtualEntityService() }
    final override val debug: PacketDebugService = PacketDebugService(this, scheduler)

    override fun send(player: Player, packet: SculkPacket): SculkResult<Unit> =
        SculkResult.failure("${backend.name} packet sending is not available for packet type ${packet.type}.")

    override fun close() {
        // Backend adapters override when they own registered listeners.
    }
}
