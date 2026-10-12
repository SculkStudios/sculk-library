package studio.sculk.capability

import studio.sculk.annotation.SculkStable

/**
 * How well the running server provides a feature, resolved once at startup (ADR-0004).
 *
 * Code that works on every state takes [service] from the three available states; code that needs
 * the native implementation matches on [Native]. The states are plain classes rather than data
 * classes so a field can be added in a minor release without breaking callers.
 */
@SculkStable
public sealed class Capability<out S : Any> {
    /** The implementation, or null when [Unavailable]. */
    public abstract val service: S?

    public val isAvailable: Boolean get() = this !is Unavailable

    /** The level as a word, for diagnostics and documentation badges. */
    public abstract val level: String

    /** Provided directly by the platform. */
    @SculkStable
    public class Native<out S : Any>(override val service: S) : Capability<S>() {
        override val level: String get() = "native"

        override fun equals(other: Any?): Boolean = other is Native<*> && other.service == service

        override fun hashCode(): Int = service.hashCode()

        override fun toString(): String = "Native($service)"
    }

    /** Reproduced by Sculk at equal fidelity; [how] says with what. */
    @SculkStable
    public class Emulated<out S : Any>(override val service: S, public val how: String) : Capability<S>() {
        override val level: String get() = "emulated"

        override fun equals(other: Any?): Boolean = other is Emulated<*> && other.service == service && other.how == how

        override fun hashCode(): Int = 31 * service.hashCode() + how.hashCode()

        override fun toString(): String = "Emulated($service, $how)"
    }

    /** Partly reproduced; [missing] lists what does not work. */
    @SculkStable
    public class Degraded<out S : Any>(override val service: S, missing: List<String>) : Capability<S>() {
        public val missing: List<String> = missing.toList()

        override val level: String get() = "degraded"

        override fun equals(other: Any?): Boolean = other is Degraded<*> && other.service == service && other.missing == missing

        override fun hashCode(): Int = 31 * service.hashCode() + missing.hashCode()

        override fun toString(): String = "Degraded($service, missing=$missing)"
    }

    /** Not provided; [reason] names what would provide it. */
    @SculkStable
    public class Unavailable(public val reason: String) : Capability<Nothing>() {
        override val service: Nothing? get() = null

        override val level: String get() = "unavailable"

        override fun equals(other: Any?): Boolean = other is Unavailable && other.reason == reason

        override fun hashCode(): Int = reason.hashCode()

        override fun toString(): String = "Unavailable($reason)"
    }
}

/**
 * Names a capability and the service type it resolves to. Keys compare by identity, so a module
 * declares each key once as a top-level value; two keys sharing an [id] are a wiring bug that
 * [Capabilities.Builder] rejects, which is what keeps a lookup from returning another key's type.
 */
@SculkStable
public class CapabilityKey<S : Any>(public val id: String, public val description: String) {
    init {
        require(ID.matches(id)) { "Capability id '$id' must be lower-case words separated by dots or hyphens." }
    }

    override fun toString(): String = id

    private companion object {
        val ID = Regex("""[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*""")
    }
}
