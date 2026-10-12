package studio.sculk.execution

import studio.sculk.annotation.SculkStable
import java.util.UUID

/**
 * What a piece of work must own to run (THREADING.md). The platform maps each context to a thread;
 * on Paper several collapse onto the main thread, on Folia and Canvas they do not.
 */
@SculkStable
public sealed interface ExecutionContext {
    /** World-wide state: time, weather, game rules, console commands, the world border. */
    @SculkStable
    public data object Global : ExecutionContext

    /** Work that touches no game state. Never a tick thread. */
    @SculkStable
    public data object Async : ExecutionContext

    /** Velocity's plugin executor. There is no main thread on a proxy. */
    @SculkStable
    public data object Proxy : ExecutionContext

    /** The chunks and blocks of one region section. */
    @SculkStable
    public data class Region(public val world: WorldKey, public val section: SectionPosition) : ExecutionContext

    /** One entity, wherever it moves. */
    @SculkStable
    public data class Entity(public val entity: EntityRef) : ExecutionContext {
        override fun toString(): String = "Entity(${entity.description})"
    }
}

/** A world's namespaced key, such as `minecraft:overworld`. Names change; keys do not. */
@SculkStable
@JvmInline
public value class WorldKey(public val value: String) {
    init {
        require(':' in value) { "A world key is namespaced, like minecraft:overworld, but was '$value'." }
    }

    override fun toString(): String = value
}

/**
 * The region section containing a chunk. A section is `2^shift` chunks wide and is always owned by
 * exactly one region, which is what makes grouping work by section safe on Folia and Canvas.
 */
@SculkStable
public data class SectionPosition(public val x: Int, public val z: Int) {
    /** The packed form Folia and Canvas use: z in the high 32 bits, x in the low 32 bits. */
    public val packed: Long get() = (z.toLong() shl 32) or (x.toLong() and 0xFFFFFFFFL)

    @SculkStable
    public companion object {
        /** Folia's and Canvas's default `grid-exponent`: sections of 16 by 16 chunks. */
        public const val DEFAULT_SHIFT: Int = 4

        public fun ofChunk(chunkX: Int, chunkZ: Int, shift: Int = DEFAULT_SHIFT): SectionPosition {
            require(shift in 0..31) { "A section shift is between 0 and 31, but was $shift." }
            return SectionPosition(chunkX shr shift, chunkZ shr shift)
        }

        public fun ofBlock(blockX: Int, blockZ: Int, shift: Int = DEFAULT_SHIFT): SectionPosition =
            ofChunk(blockX shr 4, blockZ shr 4, shift)
    }
}

/**
 * A live entity as the execution layer sees it. Platforms implement it over their own entity type;
 * identity is the [id].
 */
@SculkStable
public interface EntityRef {
    public val id: UUID

    /** For diagnostics, for example `Player Steve` or `Zombie at world 10 64 -3`. */
    public val description: String
}
