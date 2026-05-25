package studio.sculk.data.repository

import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkStable
import studio.sculk.core.scheduler.SculkScheduler
import java.util.concurrent.CompletableFuture

/** Async facade over a blocking [SculkRepository]. */
@SculkStable
public class AsyncRepository<T : Any, ID : Any> public constructor(
    private val delegate: SculkRepository<T, ID>,
    private val scheduler: SculkScheduler,
) {
    public fun find(id: ID): CompletableFuture<SculkResult<T?>> = scheduler.runAsyncResult { delegate.find(id) }

    public fun findAll(): CompletableFuture<SculkResult<List<T>>> = scheduler.runAsyncResult { delegate.findAll() }

    public fun save(entity: T): CompletableFuture<SculkResult<Unit>> = scheduler.runAsyncResult { delegate.save(entity) }

    public fun saveAll(entities: List<T>): CompletableFuture<SculkResult<Unit>> = scheduler.runAsyncResult { delegate.saveAll(entities) }

    public fun delete(id: ID): CompletableFuture<SculkResult<Unit>> = scheduler.runAsyncResult { delegate.delete(id) }

    public fun exists(id: ID): CompletableFuture<SculkResult<Boolean>> = scheduler.runAsyncResult { delegate.exists(id) }
}

/** Wraps this blocking repository in an async facade backed by [scheduler]. */
@SculkStable
public fun <T : Any, ID : Any> SculkRepository<T, ID>.async(scheduler: SculkScheduler): AsyncRepository<T, ID> =
    AsyncRepository(this, scheduler)
