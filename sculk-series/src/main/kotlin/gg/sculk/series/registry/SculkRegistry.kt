package gg.sculk.series.registry

import gg.sculk.core.annotation.SculkInternal
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe, cache-backed registry that resolves string keys to typed values.
 *
 * All lookups are cached after the first resolution. Keys are normalized to
 * lowercase and can use either underscores or hyphens interchangeably.
 *
 * This is the internal base for all SculkSeries registries. Plugin code
 * uses the typed helpers on [gg.sculk.series.SculkSeries] instead.
 */
@SculkInternal
public class SculkRegistry<T : Any>(
    private val resolver: MappingResolver<T>,
    private val versionAdapter: VersionAdapter<T>? = null,
) {
    // ConcurrentHashMap rejects null values, so we wrap results in Optional.
    private val cache: ConcurrentHashMap<String, Optional<T>> = ConcurrentHashMap()

    /**
     * Resolves [key] to a value, using the cache for subsequent calls.
     * Returns null if the key is unknown, after attempting version adaptation.
     */
    public fun resolve(key: String): T? {
        val normalized = normalize(key)
        return cache
            .getOrPut(normalized) {
                Optional.ofNullable(
                    resolver.resolve(normalized) ?: versionAdapter?.adapt(normalized),
                )
            }.orElse(null)
    }

    /**
     * Returns all known keys in this registry.
     */
    public fun keys(): Set<String> = resolver.keys()

    private fun normalize(key: String): String = key.lowercase().replace('-', '_')
}

/**
 * Resolves a string key to a typed value from the server's runtime registry.
 */
@SculkInternal
public interface MappingResolver<T : Any> {
    /** Returns the value for [key], or null if not found. */
    public fun resolve(key: String): T?

    /** Returns all keys this resolver knows about. */
    public fun keys(): Set<String>
}

/**
 * Adapts keys that were renamed or removed across Minecraft versions.
 * Tried only when the primary [MappingResolver] returns null.
 */
@SculkInternal
public interface VersionAdapter<T : Any> {
    /** Attempts to resolve a renamed [key]. Returns null if no mapping exists. */
    public fun adapt(key: String): T?
}
