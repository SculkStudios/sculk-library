package studio.sculk.data

import com.github.benmanes.caffeine.cache.Caffeine
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.map
import java.time.Duration

/**
 * A read-through cache over a repository.
 *
 * ### Negative caching
 *
 * Absent ids are remembered too, briefly. The pattern that motivates it: a join handler asks for a
 * profile, the player is new, nothing is found — and then every later lookup in that session asks
 * the database the same question and gets the same nothing. Without a negative entry a new player
 * is the *worst* case rather than the cheapest.
 *
 * The negative TTL is deliberately short. A row created by another server should become visible in
 * seconds, not on restart.
 */
@SculkStable
public class SculkCache<T : Any, ID : Any> internal constructor(
    private val repository: SculkRepository<T, ID>,
    private val idOf: (T) -> ID,
    maximumSize: Long,
    expireAfter: Duration,
    negativeExpireAfter: Duration,
) {
    private val present = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterWrite(expireAfter)
        .build<ID, T>()

    private val absent = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterWrite(negativeExpireAfter)
        .build<ID, Boolean>()

    /** The cached value, loading it if this id has not been asked about recently. */
    @SculkStable
    public suspend fun find(id: ID): SculkResult<T?> {
        present.getIfPresent(id)?.let { return SculkResult.success(it) }
        if (absent.getIfPresent(id) == true) return SculkResult.success(null)

        return repository.find(id).map { value ->
            if (value != null) present.put(id, value) else absent.put(id, true)
            value
        }
    }

    /** The cached value, or one created and saved by [create] if there is none. */
    @SculkStable
    public suspend fun findOrCreate(id: ID, create: (ID) -> T): SculkResult<T> {
        find(id).let { result ->
            when (result) {
                is SculkResult.Failure -> return result
                is SculkResult.Success -> result.value?.let { return SculkResult.success(it) }
            }
        }
        val created = create(id)
        return repository.save(created).map { created.also { put(it) } }
    }

    /** Whatever is cached for [id] right now, without touching the database. */
    @SculkStable
    public fun peek(id: ID): T? = present.getIfPresent(id)

    @SculkStable
    public suspend fun save(value: T): SculkResult<Unit> = repository.save(value).map { put(value) }

    @SculkStable
    public suspend fun delete(id: ID): SculkResult<Unit> = repository.delete(id).map {
        present.invalidate(id)
        absent.put(id, true)
    }

    /** Drops [id] from the cache without touching the row. Call on quit. */
    @SculkStable
    public fun forget(id: ID) {
        present.invalidate(id)
        absent.invalidate(id)
    }

    @SculkStable
    public fun clear() {
        present.invalidateAll()
        absent.invalidateAll()
    }

    @SculkStable
    public val size: Long get() = present.estimatedSize()

    private fun put(value: T) {
        val id = idOf(value)
        present.put(id, value)
        absent.invalidate(id)
    }
}

/**
 * Wraps this repository in a read-through cache.
 *
 * [idOf] reads an entity's key; the cache cannot infer it without reflection, and asking for it is
 * one line at the call site.
 */
@SculkStable
public fun <T : Any, ID : Any> SculkRepository<T, ID>.cached(
    idOf: (T) -> ID,
    maximumSize: Long = 10_000,
    expireAfter: Duration = Duration.ofMinutes(10),
    negativeExpireAfter: Duration = Duration.ofSeconds(30),
): SculkCache<T, ID> = SculkCache(this, idOf, maximumSize, expireAfter, negativeExpireAfter)
