package studio.sculk.placeholder

import studio.sculk.annotation.SculkStable
import java.util.UUID

/**
 * Which values of a placeholder may have changed, so a surface showing it renders again. Signalled
 * by an invalidation, and when a newly computed value differs from the one it replaces.
 *
 * A change for an audience without an `Identity.UUID` pointer, such as the console, is signalled
 * as [Everyone].
 */
@SculkStable
public sealed interface PlaceholderChange {
    public val key: PlaceholderKey

    /** Any value of the placeholder, for every audience. */
    @SculkStable
    public data class Everyone(override val key: PlaceholderKey) : PlaceholderChange

    /** The value of an audience placeholder for the audience with [uniqueId]. */
    @SculkStable
    public data class Audience(override val key: PlaceholderKey, public val uniqueId: UUID) : PlaceholderChange

    /** The value of a relational placeholder for one [viewer] seeing one [target]. */
    @SculkStable
    public data class Relation(override val key: PlaceholderKey, public val viewer: UUID, public val target: UUID) : PlaceholderChange
}
