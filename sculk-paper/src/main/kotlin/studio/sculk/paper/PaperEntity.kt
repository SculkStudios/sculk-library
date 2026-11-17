package studio.sculk.paper

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.EntityRef
import java.util.UUID

/** A Bukkit entity as the execution layer sees it. Equal when the UUIDs are equal. */
@SculkStable
public class PaperEntityRef(public val entity: Entity) : EntityRef {
    override val id: UUID = entity.uniqueId

    // Computed on demand: refs are made on every Entity.ref call, and the text is needed only for diagnostics.
    override val description: String get() = when (entity) {
        is Player -> "Player ${entity.name}"
        else -> "${entity.type.key.value()} ${entity.uniqueId}"
    }

    override fun equals(other: Any?): Boolean = other is EntityRef && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = description
}

/** This entity as an [EntityRef], for the execution layer. A new ref per call, equal to every other ref of this entity. */
@SculkStable
public val Entity.ref: EntityRef get() = PaperEntityRef(this)
