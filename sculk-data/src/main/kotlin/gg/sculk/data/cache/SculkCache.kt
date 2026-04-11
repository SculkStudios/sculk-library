package gg.sculk.data.cache

import com.github.benmanes.caffeine.cache.Caffeine
import gg.sculk.core.SculkResult
import gg.sculk.core.annotation.SculkStable
import gg.sculk.data.repository.SculkRepository
import java.time.Duration

/**
 * A [SculkRepository] wrapper that caches lookup results using Caffeine.
 *
 * Cache hits bypass the underlying repository entirely. Mutations (save, delete)
 * invalidate the affected entry.
 *
 * Example:
 * ```kotlin
 * val cached = sculk.data.cached<PlayerData, UUID> {
 *     ttl     = Duration.ofMinutes(10)
 *     maxSize = 500
 *     loader  { uuid -> repo.find(uuid) }
 * }
 * ```
 */
@SculkStable
public class SculkCache<T : Any, ID : Any>(
    private val delegate: SculkRepository<T, ID>,
    private val idExtractor: (T) -> ID,
    ttl: Duration,
    maxSize: Long,
) : SculkRepository<T, ID> {
    private val cache =
        Caffeine
            .newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build<ID, T?>()

    override fun find(id: ID): SculkResult<T?> {
        val cached = cache.getIfPresent(id)
        if (cached != null) return SculkResult.success(cached)
        // Null in Caffeine is treated as "absent" — we only cache non-null hits.
        val result = delegate.find(id)
        if (result is SculkResult.Success) {
            val v = result.value
            if (v != null) cache.put(id, v)
        }
        return result
    }

    override fun findAll(): SculkResult<List<T>> = delegate.findAll()

    override fun save(entity: T): SculkResult<Unit> {
        val result = delegate.save(entity)
        if (result is SculkResult.Success) {
            cache.put(idExtractor(entity), entity)
        }
        return result
    }

    override fun delete(id: ID): SculkResult<Unit> {
        val result = delegate.delete(id)
        if (result is SculkResult.Success) {
            cache.invalidate(id)
        }
        return result
    }

    override fun exists(id: ID): SculkResult<Boolean> {
        if (cache.getIfPresent(id) != null) return SculkResult.success(true)
        return delegate.exists(id)
    }

    /** Removes all entries from the in-memory cache without touching the database. */
    @SculkStable
    public fun invalidateAll() {
        cache.invalidateAll()
    }
}

/**
 * DSL builder for [SculkCache].
 */
@SculkStable
public class CacheBuilder<T : Any, ID : Any>(
    private val delegate: SculkRepository<T, ID>,
    private val idExtractor: (T) -> ID,
) {
    /** How long entries stay in the cache after being written. */
    public var ttl: Duration = Duration.ofMinutes(10)

    /** Maximum number of entries held in the cache. */
    public var maxSize: Long = 500

    internal fun build(): SculkCache<T, ID> = SculkCache(delegate, idExtractor, ttl, maxSize)
}
