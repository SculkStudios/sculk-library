package studio.sculk.data.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkStable
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository
import java.time.Duration

/**
 * Distributed [SculkCache] backed by Redis — share cached entities across a network of servers.
 *
 * Entities are serialized with kotlinx.serialization (so they must be `@Serializable`) and stored
 * under `"<keyPrefix>:<id>"` with a TTL. Primary-key lookups hit Redis first, then fall through to
 * the delegate repository; if Redis is unreachable, calls degrade gracefully to the delegate.
 *
 * The Redis client (Lettuce) is an opt-in dependency — add `io.lettuce:lettuce-core` to plugins
 * that use this backend.
 *
 * ```kotlin
 * val cache = RedisCache.create(
 *     delegate = repo,
 *     idExtractor = PlayerData::uuid,
 *     serializer = PlayerData.serializer(),
 *     redisUri = "redis://localhost:6379",
 *     keyPrefix = "players",
 * )
 * ```
 */
@SculkStable
public class RedisCache<T : Any, ID : Any>(
    private val delegate: SculkRepository<T, ID>,
    private val idExtractor: (T) -> ID,
    private val serializer: KSerializer<T>,
    private val backend: RedisBackend,
    private val keyPrefix: String,
    private val ttl: Duration,
    private val json: Json = DEFAULT_JSON,
) : SculkCache<T, ID>,
    SculkHandle {
    private fun key(id: ID): String = "$keyPrefix:$id"

    /** Closes the underlying Redis connection. Closed automatically when the data layer shuts down. */
    override fun close(): Unit = backend.close()

    override suspend fun find(id: ID): SculkResult<T?> {
        runCatching { backend.get(key(id)) }.getOrNull()?.let {
            return SculkResult.success(json.decodeFromString(serializer, it))
        }
        val result = delegate.find(id)
        if (result is SculkResult.Success) {
            result.value?.let { cachePut(idExtractor(it), it) }
        }
        return result
    }

    override suspend fun findAll(): SculkResult<List<T>> = delegate.findAll()

    override suspend fun save(entity: T): SculkResult<Unit> {
        val result = delegate.save(entity)
        if (result is SculkResult.Success) cachePut(idExtractor(entity), entity)
        return result
    }

    override suspend fun delete(id: ID): SculkResult<Unit> {
        val result = delegate.delete(id)
        if (result is SculkResult.Success) runCatching { backend.delete(key(id)) }
        return result
    }

    override suspend fun exists(id: ID): SculkResult<Boolean> {
        if (runCatching { backend.get(key(id)) }.getOrNull() != null) return SculkResult.success(true)
        return delegate.exists(id)
    }

    override suspend fun saveAll(entities: List<T>): SculkResult<Unit> {
        val result = delegate.saveAll(entities)
        if (result is SculkResult.Success) entities.forEach { cachePut(idExtractor(it), it) }
        return result
    }

    override suspend fun query(block: QueryBuilder<T>.() -> Unit): SculkResult<List<T>> = delegate.query(block)

    override suspend fun findOrCreate(
        id: ID,
        factory: () -> T,
    ): SculkResult<T> {
        find(id).let { found ->
            if (found is SculkResult.Success && found.value != null) return SculkResult.success(found.value!!)
        }
        val new = factory()
        return when (val saved = save(new)) {
            is SculkResult.Success -> SculkResult.success(new)
            is SculkResult.Failure -> SculkResult.failure("findOrCreate: failed to persist new entity for id $id", saved.cause)
        }
    }

    override suspend fun <R : Comparable<R>> findTopBy(
        limit: Int,
        selector: (T) -> R,
        descending: Boolean,
    ): SculkResult<List<T>> =
        when (val all = delegate.findAll()) {
            is SculkResult.Failure -> SculkResult.failure(all.message, all.cause)
            is SculkResult.Success -> {
                val sorted = if (descending) all.value.sortedByDescending(selector) else all.value.sortedBy(selector)
                SculkResult.success(sorted.take(limit))
            }
        }

    override suspend fun invalidate(id: ID) {
        runCatching { backend.delete(key(id)) }
    }

    override suspend fun invalidateAll() {
        runCatching { backend.deleteByPrefix(keyPrefix) }
    }

    private suspend fun cachePut(
        id: ID,
        value: T,
    ) {
        runCatching { backend.set(key(id), json.encodeToString(serializer, value), ttl.seconds) }
    }

    public companion object {
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

        /** Builds a [RedisCache] connected to [redisUri] using a Lettuce-backed [RedisBackend]. */
        @SculkStable
        public fun <T : Any, ID : Any> create(
            delegate: SculkRepository<T, ID>,
            idExtractor: (T) -> ID,
            serializer: KSerializer<T>,
            redisUri: String,
            keyPrefix: String,
            ttl: Duration = Duration.ofMinutes(10),
        ): RedisCache<T, ID> = RedisCache(delegate, idExtractor, serializer, LettuceRedisBackend(redisUri), keyPrefix, ttl)
    }
}

/**
 * Minimal Redis operations used by [RedisCache]. Abstracted so the client dependency stays isolated
 * to [LettuceRedisBackend] and can be stubbed in tests.
 */
@SculkStable
public interface RedisBackend : SculkHandle {
    public suspend fun get(key: String): String?

    public suspend fun set(
        key: String,
        value: String,
        ttlSeconds: Long,
    )

    public suspend fun delete(key: String)

    public suspend fun deleteByPrefix(prefix: String)
}

/** Lettuce-backed [RedisBackend]. Requires `io.lettuce:lettuce-core` on the runtime classpath. */
@SculkStable
public class LettuceRedisBackend(
    redisUri: String,
) : RedisBackend {
    private val client = RedisClient.create(redisUri)
    private val connection = client.connect()
    private val commands = connection.sync()

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) { commands.get(key) }

    override suspend fun set(
        key: String,
        value: String,
        ttlSeconds: Long,
    ) {
        withContext(Dispatchers.IO) { commands.setex(key, ttlSeconds, value) }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { commands.del(key) }
    }

    override suspend fun deleteByPrefix(prefix: String) {
        withContext(Dispatchers.IO) {
            var cursor: ScanCursor = ScanCursor.INITIAL
            val args = ScanArgs.Builder.matches("$prefix:*").limit(256)
            do {
                val result = commands.scan(cursor, args)
                if (result.keys.isNotEmpty()) commands.del(*result.keys.toTypedArray())
                cursor = result
            } while (!cursor.isFinished)
        }
    }

    override fun close() {
        connection.close()
        client.shutdown()
    }
}
