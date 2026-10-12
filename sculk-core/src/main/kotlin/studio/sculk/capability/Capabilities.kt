package studio.sculk.capability

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/** The capabilities of the running server, fixed once the runtime is ready. */
@SculkStable
public class Capabilities private constructor(private val resolved: Map<CapabilityKey<*>, Capability<*>>) {
    /** The resolved state of [key]; a key nobody provides is [Capability.Unavailable]. */
    public operator fun <S : Any> get(key: CapabilityKey<S>): Capability<S> {
        @Suppress("UNCHECKED_CAST")
        return resolved[key] as Capability<S>? ?: Capability.Unavailable("no provider on this platform")
    }

    /**
     * The service for [key] at any available level, or an [SculkFailure.UnsupportedCapability]
     * naming why it is missing. A missing capability is a fact about the server, not a bug, so this
     * returns a result rather than throwing.
     */
    public fun <S : Any> service(key: CapabilityKey<S>): SculkResult<S> = when (val capability = get(key)) {
        is Capability.Unavailable -> SculkFailure.UnsupportedCapability(key.description, capability.reason)
        is Capability.Native -> SculkResult.success(capability.service)
        is Capability.Emulated -> SculkResult.success(capability.service)
        is Capability.Degraded -> SculkResult.success(capability.service)
    }

    /** Every resolved capability, ordered by id, for diagnostics. */
    public fun all(): List<Pair<CapabilityKey<*>, Capability<*>>> = resolved.entries.map { it.key to it.value }.sortedBy { it.first.id }

    /** Collects resolutions from platform providers. One resolution per key, one key per id. */
    @SculkStable
    public class Builder {
        private val resolved = LinkedHashMap<CapabilityKey<*>, Capability<*>>()

        public fun <S : Any> resolve(key: CapabilityKey<S>, capability: Capability<S>): Builder = apply {
            check(key !in resolved) { "Capability '${key.id}' was resolved twice; one platform adapter must own it." }
            check(resolved.keys.none { it.id == key.id }) { "Two different keys use the id '${key.id}'; declare each key once." }
            resolved[key] = capability
        }

        public fun build(): Capabilities = Capabilities(LinkedHashMap(resolved))
    }

    @SculkStable
    public companion object {
        public val NONE: Capabilities = Capabilities(emptyMap())
    }
}
