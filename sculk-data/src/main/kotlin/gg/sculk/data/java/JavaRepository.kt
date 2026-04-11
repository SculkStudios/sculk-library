package gg.sculk.data.java

import gg.sculk.core.SculkResult
import gg.sculk.core.annotation.SculkStable
import gg.sculk.data.repository.SculkRepository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/**
 * Java-friendly wrapper around [SculkRepository] that returns [CompletableFuture] for every
 * operation, executing on [executor] (defaults to the common ForkJoinPool).
 *
 * In Phase 7, [SculkPlatform][gg.sculk.platform.SculkPlatform] wires this with Paper's
 * async executor instead.
 *
 * ```java
 * var repo = JavaRepository.wrap(sculk.data().repository(PlayerData.class, UUID.class));
 * repo.find(player.getUniqueId())
 *     .thenAccept(result -> {
 *         if (result instanceof SculkResult.Success<PlayerData?> s) {
 *             player.sendMessage("Coins: " + s.getValue().getCoins());
 *         }
 *     });
 * ```
 */
@SculkStable
public class JavaRepository<T : Any, ID : Any>(
    private val delegate: SculkRepository<T, ID>,
    private val executor: Executor = ForkJoinPool.commonPool(),
) {
    /** Async find by [id]. */
    public fun find(id: ID): CompletableFuture<SculkResult<T?>> = CompletableFuture.supplyAsync({ delegate.find(id) }, executor)

    /** Async findAll. */
    public fun findAll(): CompletableFuture<SculkResult<List<T>>> = CompletableFuture.supplyAsync({ delegate.findAll() }, executor)

    /** Async save. */
    public fun save(entity: T): CompletableFuture<SculkResult<Unit>> = CompletableFuture.supplyAsync({ delegate.save(entity) }, executor)

    /** Async delete by [id]. */
    public fun delete(id: ID): CompletableFuture<SculkResult<Unit>> = CompletableFuture.supplyAsync({ delegate.delete(id) }, executor)

    /** Async exists check for [id]. */
    public fun exists(id: ID): CompletableFuture<SculkResult<Boolean>> = CompletableFuture.supplyAsync({ delegate.exists(id) }, executor)

    public companion object {
        /** Wraps [repo] using the common ForkJoinPool for async execution. */
        @JvmStatic
        public fun <T : Any, ID : Any> wrap(repo: SculkRepository<T, ID>): JavaRepository<T, ID> = JavaRepository(repo)

        /** Wraps [repo] using a custom [executor]. */
        @JvmStatic
        public fun <T : Any, ID : Any> wrap(
            repo: SculkRepository<T, ID>,
            executor: Executor,
        ): JavaRepository<T, ID> = JavaRepository(repo, executor)
    }
}
