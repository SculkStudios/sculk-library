package studio.sculk.data

import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.map
import java.time.Duration
import java.util.logging.Logger

/**
 * A cache shared by every server on the network.
 *
 * [SculkCache] is per-process and is the right default. Reach for this one when two servers must
 * agree about the same row — a player hopping from lobby to survival should not arrive with a
 * balance the lobby cached ten minutes ago.
 *
 * ### Redis and Valkey
 *
 * Valkey is a fork of Redis and speaks the same protocol, so the same Lettuce client and the same
 * `redis://host:port` URI work against both. There is no `valkey://` scheme — using one is the
 * usual first thing to go wrong. Cluster and sentinel URIs (`redis-sentinel://`) work as Lettuce
 * defines them.
 *
 * ### When the cache is unreachable
 *
 * Every Redis call degrades to the repository rather than failing the read. A cache that takes the
 * server down when it blinks is worse than no cache. Failures are logged, but rate-limited to one
 * line per 30 seconds — a Redis outage otherwise writes one log line per lookup per player, which
 * is its own outage.
 *
 * ```kotlin
 * val players = RedisCache.create(
 *     repository = data.repository<PlayerData, UUID>(),
 *     idOf = PlayerData::uuid,
 *     serializer = PlayerData.serializer(),
 *     uri = "redis://localhost:6379",
 *     keyPrefix = "players",
 *     logger = plugin.logger,
 * )
 * ```
 */
@SculkStable
public class RedisCache<T : Any, ID : Any> internal constructor(
    private val repository: SculkRepository<T, ID>,
    private val idOf: (T) -> ID,
    private val serializer: KSerializer<T>,
    private val backend: RedisBackend,
    private val keyPrefix: String,
    private val ttl: Duration,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) : SculkHandle {
    // Null rather than 0: a zero sentinel compares equal to a clock that starts at zero, which
    // silently swallowed the *first* report of an outage -- the one that matters most.
    @Volatile private var lastComplaint: Long? = null

    /** True while the last Redis call succeeded. Useful on a status command. */
    @Volatile
    public var available: Boolean = true
        private set

    @SculkStable
    public suspend fun find(id: ID): SculkResult<T?> {
        cached(id)?.let { return SculkResult.success(it) }
        return repository.find(id).map { value ->
            value?.also { store(idOf(it), it) }
        }
    }

    @SculkStable
    public suspend fun findOrCreate(id: ID, create: (ID) -> T): SculkResult<T> {
        when (val found = find(id)) {
            is SculkResult.Failure -> return found
            is SculkResult.Success -> found.value?.let { return SculkResult.success(it) }
        }
        val created = create(id)
        return repository.save(created).map { created.also { store(id, it) } }
    }

    @SculkStable
    public suspend fun save(value: T): SculkResult<Unit> = repository.save(value).map { store(idOf(value), value) }

    @SculkStable
    public suspend fun delete(id: ID): SculkResult<Unit> = repository.delete(id).map { guard("evict $id") { backend.delete(key(id)) } }

    /** Drops [id] from the shared cache without touching the row. */
    @SculkStable
    public suspend fun forget(id: ID) {
        guard("forget $id") { backend.delete(key(id)) }
    }

    /** Drops every key under this prefix. */
    @SculkStable
    public suspend fun clear() {
        guard("clear $keyPrefix") { backend.deleteByPrefix(keyPrefix) }
    }

    override fun close(): Unit = backend.close()

    private fun key(id: ID) = "$keyPrefix:$id"

    private suspend fun cached(id: ID): T? {
        val raw = guard("read $id") { backend.get(key(id)) } ?: return null
        // A value that no longer decodes is a stale shape from an older version of the plugin, not
        // a reason to fail the lookup: drop it and let the repository answer.
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse {
            guard("evict an unreadable entry for $id") { backend.delete(key(id)) }
            null
        }
    }

    private suspend fun store(id: ID, value: T) {
        guard("cache $id") { backend.set(key(id), json.encodeToString(serializer, value), ttl.seconds) }
    }

    private suspend inline fun <R> guard(what: String, block: () -> R): R? = try {
        block().also { available = true }
    } catch (error: Exception) {
        available = false
        val now = clock()
        val last = lastComplaint
        if (last == null || now - last > COMPLAIN_EVERY_MILLIS) {
            lastComplaint = now
            logger.warning("[SculkData] Redis is unavailable (could not $what); falling back to the database: ${error.message}")
        }
        null
    }

    public companion object {
        private const val COMPLAIN_EVERY_MILLIS = 30_000L

        // ignoreUnknownKeys so a field added in a plugin update does not make every entry written
        // by the previous version unreadable; encodeDefaults so a value is complete on the wire
        // even if another server decodes it with different defaults.
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Connects to [uri] with Lettuce. Works against Redis and Valkey alike. */
        @SculkStable
        public fun <T : Any, ID : Any> create(
            repository: SculkRepository<T, ID>,
            idOf: (T) -> ID,
            serializer: KSerializer<T>,
            uri: String,
            keyPrefix: String,
            logger: Logger,
            ttl: Duration = Duration.ofMinutes(10),
        ): RedisCache<T, ID> = RedisCache(repository, idOf, serializer, LettuceRedisBackend(uri), keyPrefix, ttl, logger)

        /** Uses a caller-supplied backend. For tests, and for a client the plugin already owns. */
        @SculkStable
        public fun <T : Any, ID : Any> using(
            repository: SculkRepository<T, ID>,
            idOf: (T) -> ID,
            serializer: KSerializer<T>,
            backend: RedisBackend,
            keyPrefix: String,
            logger: Logger,
            ttl: Duration = Duration.ofMinutes(10),
        ): RedisCache<T, ID> = RedisCache(repository, idOf, serializer, backend, keyPrefix, ttl, logger)
    }
}

/**
 * The handful of Redis operations the cache needs.
 *
 * Kept as an interface so the Lettuce dependency stays in one class and a test can substitute a
 * map — the alternative is every consumer needing a live server to test a cache miss.
 */
@SculkStable
public interface RedisBackend : SculkHandle {
    public suspend fun get(key: String): String?

    public suspend fun set(key: String, value: String, ttlSeconds: Long)

    public suspend fun delete(key: String)

    public suspend fun deleteByPrefix(prefix: String)
}

/**
 * Lettuce-backed [RedisBackend]. Requires `io.lettuce:lettuce-core` at runtime.
 *
 * Connects to Redis and to Valkey with the same `redis://` URI.
 */
@SculkStable
public class LettuceRedisBackend(uri: String) : RedisBackend {
    private val client = RedisClient.create(uri)
    private val connection = client.connect()
    private val commands = connection.sync()

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) { commands.get(key) }

    override suspend fun set(key: String, value: String, ttlSeconds: Long) {
        withContext(Dispatchers.IO) { commands.setex(key, ttlSeconds, value) }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { commands.del(key) }
    }

    override suspend fun deleteByPrefix(prefix: String) {
        withContext(Dispatchers.IO) {
            // SCAN rather than KEYS: KEYS blocks the whole server while it walks the keyspace, and
            // on a shared Redis that stalls every other plugin using it.
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
