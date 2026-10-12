package studio.sculk.capability

import studio.sculk.annotation.SculkStable

/**
 * How well the running server provides a feature, resolved once at startup (ADR-0004).
 *
 * Code that works on every state takes [service] from the three available states; code that needs
 * the native implementation matches on [Native].
 */
@SculkStable
public sealed interface Capability<out S : Any> {
    /** Provided directly by the platform. */
    @SculkStable
    public data class Native<out S : Any>(override val service: S) : Capability<S>

    /** Reproduced by Sculk at equal fidelity; [how] says with what. */
    @SculkStable
    public data class Emulated<out S : Any>(override val service: S, public val how: String) : Capability<S>

    /** Partly reproduced; [missing] lists what does not work. */
    @SculkStable
    public data class Degraded<out S : Any>(override val service: S, public val missing: List<String>) : Capability<S>

    /** Not provided; [reason] names what would provide it. */
    @SculkStable
    public data class Unavailable(public val reason: String) : Capability<Nothing> {
        override val service: Nothing? get() = null
    }

    /** The implementation, or null when [Unavailable]. */
    public val service: S?

    public val isAvailable: Boolean get() = this !is Unavailable

    /** The level as a word, for diagnostics and documentation badges. */
    public val level: String
        get() = when (this) {
            is Native -> "native"
            is Emulated -> "emulated"
            is Degraded -> "degraded"
            is Unavailable -> "unavailable"
        }
}

/**
 * Names a capability and the service type it resolves to. Keys are compared by [id], so a module
 * declares each key once as a top-level value.
 */
@SculkStable
public class CapabilityKey<S : Any>(public val id: String, public val description: String) {
    init {
        require(ID.matches(id)) { "Capability id '$id' must be lower-case words separated by dots or hyphens." }
    }

    override fun equals(other: Any?): Boolean = other is CapabilityKey<*> && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id

    private companion object {
        val ID = Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")
    }
}
