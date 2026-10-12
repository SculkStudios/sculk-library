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

    /** The service for [key], or an [SculkFailure.UnsupportedCapability] naming why it is missing. */
    public fun <S : Any> require(key: CapabilityKey<S>): SculkResult<S> = when (val capability = get(key)) {
        is Capability.Unavailable -> SculkFailure.UnsupportedCapability(key.description, capability.reason)
        else -> SculkResult.success(checkNotNull(capability.service))
    }

    /** Every resolved capability, ordered by id, for diagnostics. */
    public fun all(): List<Pair<CapabilityKey<*>, Capability<*>>> = resolved.entries.map { it.key to it.value }.sortedBy { it.first.id }

    /** Collects resolutions from platform providers. One resolution per key. */
    @SculkStable
    public class Builder {
        private val resolved = LinkedHashMap<CapabilityKey<*>, Capability<*>>()

        public fun <S : Any> resolve(key: CapabilityKey<S>, capability: Capability<S>): Builder = apply {
            check(key !in resolved) { "Capability '${key.id}' was resolved twice; one platform adapter must own it." }
            resolved[key] = capability
        }

        public fun build(): Capabilities = Capabilities(LinkedHashMap(resolved))
    }

    @SculkStable
    public companion object {
        public val NONE: Capabilities = Capabilities(emptyMap())
    }
}
