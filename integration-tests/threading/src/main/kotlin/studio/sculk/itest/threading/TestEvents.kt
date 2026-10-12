package studio.sculk.itest.threading

import org.bukkit.entity.Entity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.entity.EntityEvent

/** A cancellable event the suite fires itself, so it can check cancellation and observer timing. */
class PurchaseEvent(val price: Int) :
    Event(),
    Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        // Bukkit finds a handler list through this static method by reflection.
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

/** An entity event, so observers must resume on the entity's own region. */
class InspectEvent(entity: Entity) : EntityEvent(entity) {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
