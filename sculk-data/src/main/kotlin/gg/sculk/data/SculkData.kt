package gg.sculk.data

import gg.sculk.config.yaml.YamlMapper
import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkStable
import gg.sculk.data.cache.CacheBuilder
import gg.sculk.data.cache.SculkCache
import gg.sculk.data.driver.ConnectionPool
import gg.sculk.data.driver.StorageConfig
import gg.sculk.data.repository.JdbcRepository
import gg.sculk.data.repository.SculkRepository
import gg.sculk.data.repository.jdbcRepository
import java.io.File
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Entry point for Sculk Studio's data layer.
 *
 * Loaded by `SculkPlatform` in Phase 7 — plugin code accesses this via `sculk.data`.
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

    /** Closes the underlying connection pool. Call this from your plugin's `onDisable`. */
    override fun close() {
        if (dataSource is AutoCloseable) dataSource.close()
    }

    public companion object {
        /**
         * Creates a [SculkData] instance, loading storage config from [dataFolder]/storage.yml.
         * The file is written with defaults if it does not exist.
         */
        @SculkStable
        @JvmStatic
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
        @JvmStatic
        public fun withDataSource(
            dataSource: DataSource,
            dialect: gg.sculk.data.driver.SqlDialect,
        ): SculkData = SculkData(dataSource, StorageConfig(type = dialect.name.lowercase()))
    }
}
