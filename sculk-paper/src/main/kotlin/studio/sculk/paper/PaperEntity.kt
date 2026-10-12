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

    override val description: String = when (entity) {
        is Player -> "Player ${entity.name}"
        else -> "${entity.type.key.value()} ${entity.uniqueId}"
    }

    override fun equals(other: Any?): Boolean = other is EntityRef && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = description
}

/** This entity as an [EntityRef], for the execution layer. */
@SculkStable
public val Entity.ref: EntityRef get() = PaperEntityRef(this)
