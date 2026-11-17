package studio.sculk.scene

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkExperimental
import java.util.UUID

/** Where a scene stands: a fixed place, or following an entity. */
@SculkExperimental
public sealed class Anchor {
    /**
     * A fixed [location]. Its yaw and pitch turn displays whose billboard is `FIXED`. The scene's
     * entities live in the region owning this location's chunk, and appear once that chunk loads.
     */
    @SculkExperimental
    public class At internal constructor(location: Location) : Anchor() {
        private val at: Location = location.clone()

        /** A copy of the location; changing it moves nothing. */
        public val location: Location get() = at.clone()

        override fun toString(): String = "At(${if (at.isWorldLoaded) at.world?.name else "unloaded world"} ${at.x}, ${at.y}, ${at.z})"
    }

    /**
     * Above, beside or below [entity], [offset] blocks from its position in world axes. The displays
     * follow it every [periodTicks], gliding over the scene's `teleportTicks`, and across regions,
     * worlds and teleports. The scene closes when the entity is removed from the world, as when a
     * mob dies, a player quits or the entity's chunk unloads.
     */
    @SculkExperimental
    public class Following internal constructor(public val entity: Entity, public val offset: Vec3, public val periodTicks: Long) :
        Anchor() {
        override fun toString(): String = "Following(${entity.uniqueId}, $offset, every $periodTicks ticks)"
    }

    @SculkExperimental
    public companion object {
        /** A fixed place; throws [IllegalArgumentException] for a location without a world. */
        public fun at(location: Location): At {
            requireNotNull(location.world) { "a scene anchor needs a location in a world: $location" }
            return At(location)
        }

        /** Follows [entity] at [offset], checking where it is every [periodTicks], at least one. */
        public fun following(entity: Entity, offset: Vec3 = Vec3.ZERO, periodTicks: Long = 1): Following {
            require(periodTicks >= 1) { "a scene follows at least once a tick apart, not every $periodTicks ticks" }
            return Following(entity, offset, periodTicks)
        }
    }
}

/**
 * Who sees a scene. [Everyone] in range sees it, except players hidden with `Scene.hide`; [Only]
 * the chosen players do, kept by id so a player who leaves and joins again still sees it.
 */
@SculkExperimental
public sealed class Visibility {
    @SculkExperimental
    public data object Everyone : Visibility()

    @SculkExperimental
    public class Only internal constructor(viewers: Collection<UUID>) : Visibility() {
        public val viewers: Set<UUID> = viewers.toSet()

        override fun equals(other: Any?): Boolean = other is Only && other.viewers == viewers

        override fun hashCode(): Int = viewers.hashCode()

        override fun toString(): String = "Only($viewers)"
    }

    @SculkExperimental
    public companion object {
        /** Only [players], and players added later with `Scene.show`. */
        public fun only(vararg players: Player): Only = Only(players.map { it.uniqueId })

        /** Only the players with these ids, online now or later. */
        public fun only(viewers: Collection<UUID>): Only = Only(viewers)
    }
}

/**
 * Who sees a scene at one moment: everyone except [listed], or only [listed]. Immutable; each show
 * or hide makes a new one.
 */
internal class Viewers(val everyone: Boolean, val listed: Set<UUID>) {
    fun sees(viewer: UUID): Boolean = if (everyone) viewer !in listed else viewer in listed

    fun show(viewer: UUID): Viewers = if (everyone) Viewers(true, listed - viewer) else Viewers(false, listed + viewer)

    fun hide(viewer: UUID): Viewers = if (everyone) Viewers(true, listed + viewer) else Viewers(false, listed - viewer)

    fun asVisibility(): Visibility = if (everyone) Visibility.Everyone else Visibility.Only(listed)

    /** The players whose view changes going from this to [next]; only they need a show or a hide. */
    fun changed(next: Viewers): Set<UUID> {
        check(next.everyone == everyone) { "a scene keeps its kind of visibility" }
        return (listed - next.listed) + (next.listed - listed)
    }

    companion object {
        fun of(visibility: Visibility): Viewers = when (visibility) {
            Visibility.Everyone -> Viewers(true, emptySet())
            is Visibility.Only -> Viewers(false, visibility.viewers)
        }
    }
}
