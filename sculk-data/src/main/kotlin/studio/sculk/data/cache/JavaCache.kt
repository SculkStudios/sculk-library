package studio.sculk.data.cache

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.data.repository.JavaRepository
import studio.sculk.data.repository.dataFuture
import studio.sculk.data.repository.dataVoidFuture
import java.util.concurrent.CompletableFuture

/**
 * A Java-friendly view over a [SculkCache]. Inherits all [JavaRepository] operations and adds the
 * cache-specific ones, each as a [CompletableFuture] completing on a background thread.
 *
 * ```java
 * JavaCache<PlayerData, UUID> cache = sculk.getData().javaCache(myCache);
 *
 * cache.findOrCreate(id, () -> new PlayerData(id, 0))
 *      .thenAccept(result -> { PlayerData data = result.getOrNull(); });
 *
 * cache.findTopBy(10, PlayerData::coins)
 *      .thenAccept(result -> renderLeaderboard(result.getOrNull()));
 * ```
 */
@SculkStable
public class JavaCache<T : Any, ID : Any> internal constructor(private val cache: SculkCache<T, ID>) : JavaRepository<T, ID>(cache) {
    /** Returns the cached/stored entity for [id], creating and persisting one via [factory] if absent. */
    @SculkStable
    public fun findOrCreate(id: ID, factory: () -> T): CompletableFuture<SculkResult<T>> = dataFuture { cache.findOrCreate(id, factory) }

    /** Returns the top [limit] entities sorted by [selector] (descending by default). */
    @JvmOverloads
    @SculkStable
    public fun <R : Comparable<R>> findTopBy(
        limit: Int,
        selector: (T) -> R,
        descending: Boolean = true,
    ): CompletableFuture<SculkResult<List<T>>> = dataFuture { cache.findTopBy(limit, selector, descending) }

    /** Removes the cached entry for [id] without touching the delegate. */
    @SculkStable
    public fun invalidate(id: ID): CompletableFuture<Void> = dataVoidFuture { cache.invalidate(id) }

    /** Clears all cached entries without touching the delegate. */
    @SculkStable
    public fun invalidateAll(): CompletableFuture<Void> = dataVoidFuture { cache.invalidateAll() }
}
