package studio.sculk.discord

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * The state a button or modal carries, encoded into the 100 characters Discord allows it.
 *
 * Discord gives an interactive component no server-side session — the custom id is round-tripped
 * through the client and handed back on click, so it is the *entire* memory a handler has. Two things
 * follow, and both are load-bearing:
 *
 * 1. **It is input, not memory.** A returning id has been outside the process. [parse] validates
 *    shape and nothing more; every name in it must be re-resolved against live state.
 * 2. **It carries no authority.** Nothing here says a click is permitted. That is decided when the
 *    click arrives, from whatever the actor's roles are *then* — so revoking a role takes effect on
 *    buttons that were posted before it was revoked, and an alert sitting in a channel for a week
 *    grants nothing it did not grant on the day.
 *
 * ```kotlin
 * val id = ComponentId.of("punish", "ban", recordId).getOrThrow()
 * // …later, on click…
 * val parsed = ComponentId.parse(clicked) ?: return   // not ours, or malformed
 * if (parsed.namespace != "punish") return
 * ```
 */
@SculkStable
public class ComponentId private constructor(
    /** Which handler owns this component. Ids from other plugins in the channel are ignored by it. */
    public val namespace: String,
    public val parts: List<String>,
) {
    /** The wire form: `namespace:part:part`. */
    public val encoded: String get() = (listOf(namespace) + parts).joinToString(SEPARATOR.toString())

    /** The part at [index], or null. Named separately so a shape change is a null rather than a throw. */
    public fun part(index: Int): String? = parts.getOrNull(index)

    override fun toString(): String = encoded

    override fun equals(other: Any?): Boolean = other is ComponentId && other.encoded == encoded

    override fun hashCode(): Int = encoded.hashCode()

    @SculkStable
    public companion object {
        /** Discord's hard limit on a component's custom id. */
        public const val MAX_LENGTH: Int = 100

        private const val SEPARATOR = ':'
        private val SAFE = Regex("[A-Za-z0-9_.-]*")

        /**
         * Builds an id, or fails saying why.
         *
         * **Failure rather than truncation, deliberately.** Cutting an over-long id to fit looks
         * harmless and is the worst available outcome: a 36-character UUID trimmed to fit still
         * parses, still passes every validation below, and then matches no record — so the button
         * renders, clicks, and reports that the thing it points at does not exist. A long namespace
         * plus a UUID plus one more part clears 100 characters, so this is reachable rather than
         * theoretical.
         */
        public fun of(namespace: String, vararg parts: String): SculkResult<ComponentId> {
            if (namespace.isEmpty()) {
                return SculkResult.failure("A component id needs a namespace so other plugins' components can be ignored.")
            }
            for (segment in listOf(namespace) + parts) {
                if (!segment.matches(SAFE)) {
                    return SculkResult.failure(
                        "Component id segment '$segment' may only contain letters, digits, '_', '.' and '-'. " +
                            "'$SEPARATOR' is the separator and cannot appear inside a segment.",
                    )
                }
            }
            val id = ComponentId(namespace, parts.toList())
            if (id.encoded.length > MAX_LENGTH) {
                return SculkResult.failure(
                    "Component id '${id.encoded}' is ${id.encoded.length} characters; Discord allows $MAX_LENGTH. " +
                        "Shorten the namespace or store the payload and reference it by a shorter key.",
                )
            }
            return SculkResult.success(id)
        }

        /**
         * Reads an id back, or null when it is not one of ours.
         *
         * Null covers both "another plugin's button in the same channel" and "malformed", because a
         * handler's correct response to each is identical: do nothing, quietly. Anything that throws
         * here surfaces to a user as a button that spins and then times out.
         */
        public fun parse(encoded: String): ComponentId? {
            if (encoded.isEmpty() || encoded.length > MAX_LENGTH) return null
            val segments = encoded.split(SEPARATOR)
            if (segments.any { !it.matches(SAFE) }) return null
            val namespace = segments.first()
            if (namespace.isEmpty()) return null
            return ComponentId(namespace, segments.drop(1))
        }
    }
}
