package studio.sculk.packets

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player
import studio.sculk.SculkResult
import java.util.concurrent.atomic.AtomicInteger

/**
 * Records what would have been sent instead of sending it.
 *
 * Shipped as a fixture because the interesting assertions about virtual entities are about how
 * *many* packets went and when — "an unchanged hologram sends nothing on reconcile" is the whole
 * point of the dirty tracking, and it cannot be observed against a real backend without a server.
 */
public class FakeVirtualEntityService : VirtualEntityService {
    public sealed interface Sent {
        public val player: Player

        public data class Spawn(
            override val player: Player,
            val entityId: Int,
            val location: Location,
            val text: Component,
            val style: TextDisplayStyle,
        ) : Sent

        public data class Update(override val player: Player, val entityId: Int, val text: Component, val style: TextDisplayStyle) : Sent

        public data class Teleport(override val player: Player, val entityId: Int, val location: Location) : Sent

        public data class Mount(override val player: Player, val vehicleId: Int, val passengerIds: List<Int>) : Sent

        public data class Despawn(override val player: Player, val entityIds: List<Int>) : Sent
    }

    private val nextId = AtomicInteger(Int.MAX_VALUE)

    /** Everything that would have gone to a client, in order. */
    public val sent: MutableList<Sent> = mutableListOf()

    override var available: Boolean = true

    /** Drops the record. Call between phases so a count means what the test thinks it means. */
    public fun clear(): Unit = sent.clear()

    /** How many packets [player] would have received. */
    public fun countFor(player: Player): Int = sent.count { it.player == player }

    override fun reserveEntityId(): Int = nextId.getAndDecrement()

    override fun spawnTextDisplay(
        player: Player,
        entityId: Int,
        location: Location,
        text: Component,
        style: TextDisplayStyle,
    ): SculkResult<Unit> = record(Sent.Spawn(player, entityId, location, text, style))

    override fun updateTextDisplay(player: Player, entityId: Int, text: Component, style: TextDisplayStyle): SculkResult<Unit> =
        record(Sent.Update(player, entityId, text, style))

    override fun teleport(player: Player, entityId: Int, location: Location): SculkResult<Unit> =
        record(Sent.Teleport(player, entityId, location))

    override fun mount(player: Player, vehicleId: Int, passengerIds: List<Int>): SculkResult<Unit> =
        record(Sent.Mount(player, vehicleId, passengerIds))

    override fun despawn(player: Player, entityIds: List<Int>): SculkResult<Unit> = record(Sent.Despawn(player, entityIds))

    private fun record(packet: Sent): SculkResult<Unit> {
        if (!available) return SculkResult.failure("The fake backend is marked unavailable.")
        sent += packet
        return SculkResult.ok()
    }
}
