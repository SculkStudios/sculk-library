package studio.sculk.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

// Java data bridges run on Dispatchers.IO — the large pool sized for blocking JDBC work — rather than
// the shared ForkJoinPool.commonPool, so heavy concurrent use never starves the common pool.
private val ioExecutor = Dispatchers.IO.asExecutor()

/**
 * Runs a `suspend` block on the IO thread pool and completes a [CompletableFuture] with its result.
 * Shared by every Java data bridge so they behave identically.
 */
internal fun <R> dataFuture(block: suspend () -> R): CompletableFuture<R> =
    CompletableFuture.supplyAsync({ runBlocking { block() } }, ioExecutor)

/** Like [dataFuture] but for `Unit`-returning operations — completes a `CompletableFuture<Void>`. */
internal fun dataVoidFuture(block: suspend () -> Unit): CompletableFuture<Void> =
    CompletableFuture.runAsync({ runBlocking { block() } }, ioExecutor)

/**
 * A Java-friendly view over a [SculkRepository] whose `suspend` methods are exposed as
 * [CompletableFuture]s. Works for plain repositories and for caches (a
 * [studio.sculk.data.cache.SculkCache] is a [SculkRepository]).
 *
 * Each call runs the operation on a background thread and completes the future there — **never on the
 * main thread**. Hop back with the scheduler before touching the Paper API:
 *
 * ```java
 * JavaRepository<PlayerData, UUID> repo = sculk.getData().javaRepository(PlayerData.class, UUID.class);
 *
 * repo.find(player.getUniqueId()).thenAccept(result -> {
 *     PlayerData data = result.getOrNull();
 *     int coins = data == null ? 0 : data.coins();
 *     sculk.getScheduler().runSync(player, () -> player.sendMessage("Coins: " + coins));
 * });
 * ```
 */
@SculkStable
public open class JavaRepository<T : Any, ID : Any> internal constructor(private val delegate: SculkRepository<T, ID>) {
    /** Finds the entity with [id], or null. */
    @SculkStable
    public fun find(id: ID): CompletableFuture<SculkResult<T?>> = dataFuture { delegate.find(id) }

    /** Returns all entities in the table. */
    @SculkStable
    public fun findAll(): CompletableFuture<SculkResult<List<T>>> = dataFuture { delegate.findAll() }

    /** Inserts or updates [entity] based on its primary key. */
    @SculkStable
    public fun save(entity: T): CompletableFuture<SculkResult<Unit>> = dataFuture { delegate.save(entity) }

    /** Deletes the entity with [id]. No-op if not found. */
    @SculkStable
    public fun delete(id: ID): CompletableFuture<SculkResult<Unit>> = dataFuture { delegate.delete(id) }

    /** Returns true if an entity with [id] exists. */
    @SculkStable
    public fun exists(id: ID): CompletableFuture<SculkResult<Boolean>> = dataFuture { delegate.exists(id) }

    /** Inserts or updates all [entities] in one batched transaction. */
    @SculkStable
    public fun saveAll(entities: List<T>): CompletableFuture<SculkResult<Unit>> = dataFuture { delegate.saveAll(entities) }

    /**
     * Runs a query. The [QueryBuilder] DSL is Kotlin-oriented (infix conditions, property
     * references); from Java prefer [findAll] + stream filtering for complex predicates.
     */
    @SculkStable
    public fun query(block: Consumer<QueryBuilder<T>>): CompletableFuture<SculkResult<List<T>>> =
        dataFuture { delegate.query { block.accept(this) } }
}
