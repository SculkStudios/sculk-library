package studio.sculk.placeholder

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.identity.Identity
import studio.sculk.annotation.SculkStable
import java.util.UUID

/**
 * What a placeholder's resolver sees: its [key] and the arguments of the tag being resolved. A
 * global placeholder sees exactly this; audience and relational placeholders add who it is for.
 */
@SculkStable
public open class PlaceholderContext internal constructor(
    public val key: PlaceholderKey,
    private val declared: List<PlaceholderArgument<*>>,
    private val values: List<Any>,
) {
    /**
     * The value of [argument]: what the tag gave, or its default. Reading an argument the
     * placeholder did not declare is a wiring bug and throws.
     */
    public operator fun <T : Any> get(argument: PlaceholderArgument<T>): T {
        val index = declared.indexOfFirst { it === argument }
        check(index >= 0) { "$argument is not an argument of <${key.tag}>." }
        @Suppress("UNCHECKED_CAST")
        return values[index] as T
    }
}

/** A resolver's view of an audience placeholder: the [audience] whose value it computes. */
@SculkStable
public class AudienceContext internal constructor(
    key: PlaceholderKey,
    declared: List<PlaceholderArgument<*>>,
    values: List<Any>,
    public val audience: Audience,
) : PlaceholderContext(key, declared, values) {
    /** The audience's `Identity.UUID` pointer: a player's id on Paper and Velocity, null for the console. */
    public val uniqueId: UUID? get() = audience.getOrDefault(Identity.UUID, null)
}

/** A resolver's view of a relational placeholder: one [viewer] seeing one [target], as with a nametag. */
@SculkStable
public class RelationalContext internal constructor(
    key: PlaceholderKey,
    declared: List<PlaceholderArgument<*>>,
    values: List<Any>,
    public val viewer: Audience,
    public val target: Audience,
) : PlaceholderContext(key, declared, values) {
    /** The viewer's `Identity.UUID` pointer, null for an audience without one. */
    public val viewerId: UUID? get() = viewer.getOrDefault(Identity.UUID, null)

    /** The target's `Identity.UUID` pointer, null for an audience without one. */
    public val targetId: UUID? get() = target.getOrDefault(Identity.UUID, null)
}
