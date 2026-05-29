package studio.sculk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.sculk.config.yaml.YamlMapper
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkStable
import studio.sculk.data.cache.CacheBuilder
import studio.sculk.data.cache.SculkCache
import studio.sculk.data.driver.ConnectionPool
import studio.sculk.data.driver.StorageConfig
import studio.sculk.data.repository.JdbcRepository
import studio.sculk.data.repository.PlayerProfileStore
import studio.sculk.data.repository.SculkRepository
import studio.sculk.data.repository.jdbcRepository
import java.io.File
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Entry point for Sculk Studio's data layer.
 *
 * Enabled via `data()` on `SculkPlatform`; plugin code accesses it through `sculk.data`.
 *
 * Direct instantiation:
 * ```kotlin
 * val data = SculkData.create(dataFolder, logger)
 * val repo = data.repository<PlayerData, UUID>()
 * val cached = data.cached(repo, { it.uuid }) { ttl = Duration.ofMinutes(10) }
 * // on disable:
 * data.close()
 * ```
 */
@SculkStable
public class SculkData private constructor(
    private val dataSource: DataSource,
    private val config: StorageConfig,
) : SculkHandle {
    // Distributed caches hold open Redis connections; close them when the data layer shuts down.
    private val managedHandles = java.util.concurrent.CopyOnWriteArrayList<SculkHandle>()

    /**
     * Creates a [JdbcRepository] for entity class [T] with primary key type [ID].
     * The table is created if it does not exist.
     */
    @SculkStable
    public fun <T : Any, ID : Any> repository(
        klass: KClass<T>,
        idClass: KClass<ID>,
    ): SculkRepository<T, ID> = jdbcRepository(dataSource, klass, config.dialect())

    /**
     * Kotlin reified convenience — creates a [JdbcRepository] for [T]/[ID].
     */
    @SculkStable
    public inline fun <reified T : Any, reified ID : Any> repository(): SculkRepository<T, ID> = repository(T::class, ID::class)

    /**
     * Wraps [delegate] in a Caffeine-backed [SculkCache].
     *
     * [idExtractor] must return the primary key value from an entity instance.
     *
     * ```kotlin
     * val cached = data.cached(repo, { it.uuid }) {
     *     ttl     = Duration.ofMinutes(10)
     *     maxSize = 500
     * }
     * ```
     */
    @SculkStable
    public fun <T : Any, ID : Any> cached(
        delegate: SculkRepository<T, ID>,
        idExtractor: (T) -> ID,
        block: CacheBuilder<T, ID>.() -> Unit = {},
    ): SculkCache<T, ID> = CacheBuilder(delegate, idExtractor).apply(block).build()

    /**
     * Wraps [delegate] in a distributed [studio.sculk.data.cache.RedisCache] for multi-server setups.
     *
     * Entities must be `@Serializable`. Requires `io.lettuce:lettuce-core` on the runtime classpath.
     *
     * ```kotlin
     * val cache = data.distributedCache(repo, PlayerData::uuid, PlayerData.serializer(), "redis://localhost", "players")
     * ```
     */
    @SculkStable
    public fun <T : Any, ID : Any> distributedCache(
        delegate: SculkRepository<T, ID>,
        idExtractor: (T) -> ID,
        serializer: kotlinx.serialization.KSerializer<T>,
        redisUri: String,
        keyPrefix: String,
        ttl: java.time.Duration = java.time.Duration.ofMinutes(10),
    ): studio.sculk.data.cache.RedisCache<T, ID> =
        studio.sculk.data.cache.RedisCache
            .create(delegate, idExtractor, serializer, redisUri, keyPrefix, ttl)
            .also { managedHandles += it }

    /** Creates a UUID-first player profile workflow around a repository. */
    @SculkStable
    public fun <T : Any, ID : Any> playerProfiles(
        repository: SculkRepository<T, ID>,
        create: (ID) -> T,
    ): PlayerProfileStore<T, ID> = PlayerProfileStore(repository, create)

    /**
     * Runs [block] inside a single database transaction on [Dispatchers.IO].
     *
     * The block receives a [java.sql.Connection] with auto-commit disabled. Returning normally
     * commits; throwing rolls back. The result is wrapped in a [SculkResult].
     *
     * ```kotlin
     * sculk.scope.launchAsync {
     *     sculk.data.transaction { conn ->
     *         conn.prepareStatement("UPDATE accounts SET coins = coins - ? WHERE id = ?").use { ... }
     *         conn.prepareStatement("UPDATE accounts SET coins = coins + ? WHERE id = ?").use { ... }
     *     }
     * }
     * ```
     */
    @SculkStable
    public suspend fun <R> transaction(block: (java.sql.Connection) -> R): SculkResult<R> =
        withContext(Dispatchers.IO) {
            runCatching {
                dataSource.connection.use { conn ->
                    val previousAutoCommit = conn.autoCommit
                    conn.autoCommit = false
                    try {
                        val result = block(conn)
                        conn.commit()
                        result
                    } catch (e: Throwable) {
                        conn.rollback()
                        throw e
                    } finally {
                        conn.autoCommit = previousAutoCommit
                    }
                }
            }.fold(
                onSuccess = { SculkResult.success(it) },
                onFailure = { SculkResult.failure("transaction failed: ${it.message}", it) },
            )
        }

    /** Closes distributed caches and the underlying connection pool. Call from your plugin's `onDisable`. */
    override fun close() {
        managedHandles.forEach { runCatching { it.close() } }
        managedHandles.clear()
        if (dataSource is AutoCloseable) dataSource.close()
    }

    public companion object {
        /**
         * Creates a [SculkData] instance, loading storage config from [dataFolder]/storage.yml.
         * The file is written with defaults if it does not exist.
         */
        @SculkStable
        public fun create(
            dataFolder: File,
            logger: Logger,
        ): SculkData {
            val configFile = File(dataFolder, "storage.yml")
            YamlMapper.writeDefaults(configFile, StorageConfig::class)
            val config = YamlMapper.load(configFile, StorageConfig::class)
            val violations = YamlMapper.validate(config)
            if (violations.isNotEmpty()) {
                violations.forEach { logger.warning("[SculkData] storage.yml: $it") }
            }
            val dataSource = ConnectionPool.create(config, dataFolder)
            logger.info("[SculkData] Connected (${config.type})")
            return SculkData(dataSource, config)
        }

        /**
         * Creates a [SculkData] instance with a pre-configured [DataSource].
         * Useful for testing with an H2 in-memory database.
         */
        @SculkStable
        public fun withDataSource(
            dataSource: DataSource,
            dialect: studio.sculk.data.driver.SqlDialect,
        ): SculkData = SculkData(dataSource, StorageConfig(type = dialect.name.lowercase()))
    }
}
