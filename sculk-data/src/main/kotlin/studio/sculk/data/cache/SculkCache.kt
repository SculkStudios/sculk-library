package studio.sculk.data.cache

import com.github.benmanes.caffeine.cache.Caffeine
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository
import java.time.Duration

/**
 * A caching [SculkRepository] that fronts a delegate repository.
 *
 * Primary-key lookups are served from the cache; mutations write through to the delegate and update
 * the cache. Two implementations are provided:
 * - [CaffeineCache] — in-process, single-server (the default via [studio.sculk.data.SculkData.cached]).
 * - [studio.sculk.data.cache.RedisCache] — distributed across servers, for networks sharing player data.
 *
 * Both expose the same surface, so swapping backends never changes call sites.
 */
@SculkStable
public interface SculkCache<T : Any, ID : Any> : SculkRepository<T, ID> {
    /** Returns the cached/stored entity for [id], creating and persisting one via [factory] if absent. */
    @SculkStable
    public suspend fun findOrCreate(id: ID, factory: () -> T): SculkResult<T>

    /** Returns the top [limit] entities sorted by [selector]. Always reads through to the delegate. */
    @SculkStable
    public suspend fun <R : Comparable<R>> findTopBy(limit: Int, selector: (T) -> R, descending: Boolean = true): SculkResult<List<T>>

    /** Removes the cached entry for [id] without touching the delegate. */
    @SculkStable
    public suspend fun invalidate(id: ID)

    /** Clears all cached entries without touching the delegate. */
    @SculkStable
    public suspend fun invalidateAll()
}

/**
 * In-process [SculkCache] backed by Caffeine.
 *
 * Cache hits bypass the delegate entirely; mutations write through and refresh the cache.
 */
@SculkStable
public class CaffeineCache<T : Any, ID : Any>(
    private val delegate: SculkRepository<T, ID>,
    private val idExtractor: (T) -> ID,
    ttl: Duration,
    maxSize: Long,
) : SculkCache<T, ID> {
    private val cache =
        Caffeine
            .newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .build<ID, T?>()

    override suspend fun find(id: ID): SculkResult<T?> {
        cache.getIfPresent(id)?.let { return SculkResult.success(it) }
        val result = delegate.find(id)
        if (result is SculkResult.Success) {
            result.value?.let { cache.put(id, it) }
        }
        return result
    }

    override suspend fun findAll(): SculkResult<List<T>> = delegate.findAll()

    override suspend fun save(entity: T): SculkResult<Unit> {
        val result = delegate.save(entity)
        if (result is SculkResult.Success) cache.put(idExtractor(entity), entity)
        return result
    }

    override suspend fun delete(id: ID): SculkResult<Unit> {
        val result = delegate.delete(id)
        if (result is SculkResult.Success) cache.invalidate(id)
        return result
    }

    override suspend fun exists(id: ID): SculkResult<Boolean> {
        if (cache.getIfPresent(id) != null) return SculkResult.success(true)
        return delegate.exists(id)
    }

    override suspend fun saveAll(entities: List<T>): SculkResult<Unit> {
        val result = delegate.saveAll(entities)
        if (result is SculkResult.Success) entities.forEach { cache.put(idExtractor(it), it) }
        return result
    }

    override suspend fun query(block: QueryBuilder<T>.() -> Unit): SculkResult<List<T>> = delegate.query(block)

    override suspend fun findOrCreate(id: ID, factory: () -> T): SculkResult<T> {
        cache.getIfPresent(id)?.let { return SculkResult.success(it) }

        val found = delegate.find(id)
        if (found is SculkResult.Success) {
            found.value?.let {
                cache.put(id, it)
                return SculkResult.success(it)
            }
        }

        val new = factory()
        return when (val saved = delegate.save(new)) {
            is SculkResult.Success -> {
                cache.put(id, new)
                SculkResult.success(new)
            }
            is SculkResult.Failure -> SculkResult.failure("findOrCreate: failed to persist new entity for id $id", saved.cause)
        }
    }

    override suspend fun <R : Comparable<R>> findTopBy(limit: Int, selector: (T) -> R, descending: Boolean): SculkResult<List<T>> =
        when (val all = delegate.findAll()) {
            is SculkResult.Failure -> SculkResult.failure(all.message, all.cause)
            is SculkResult.Success -> {
                val sorted = if (descending) all.value.sortedByDescending(selector) else all.value.sortedBy(selector)
                SculkResult.success(sorted.take(limit))
            }
        }

    override suspend fun invalidate(id: ID): Unit = cache.invalidate(id)

    override suspend fun invalidateAll(): Unit = cache.invalidateAll()
}

/**
 * DSL builder for a [CaffeineCache].
 */
@SculkStable
public class CacheBuilder<T : Any, ID : Any>(private val delegate: SculkRepository<T, ID>, private val idExtractor: (T) -> ID) {
    /** How long entries stay in the cache after being written. */
    public var ttl: Duration = Duration.ofMinutes(10)

    /** Maximum number of entries held in the cache. */
    public var maxSize: Long = 500

    internal fun build(): CaffeineCache<T, ID> = CaffeineCache(delegate, idExtractor, ttl, maxSize)
}
