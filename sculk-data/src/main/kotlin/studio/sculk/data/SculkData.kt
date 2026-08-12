package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.io.File
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * The database, and the repositories over it.
 *
 * ```kotlin
 * val players = data.repository<PlayerData, UUID>()
 * val profile = players.find(uuid).getOrNull()
 * ```
 */
@SculkStable
public class SculkData
@SculkInternal
constructor(
    private val dataSource: DataSource,
    public val dialect: SqlDialect,
    private val logger: Logger,
    private val owned: Boolean = true,
) : SculkHandle {
    private val repositories = ConcurrentHashMap<String, SculkRepository<*, *>>()

    /** Which backend is in use, for the start-up banner and for support questions. */
    public val backend: String get() = dialect.name.lowercase()

    @SculkStable
    public inline fun <reified T : Any, ID : Any> repository(): SculkRepository<T, ID> = repository(serializer<T>())

    /** The repository for [serializer]'s entity, migrating its table on first use. */
    @Suppress("UNCHECKED_CAST")
    @SculkStable
    public fun <T : Any, ID : Any> repository(serializer: KSerializer<T>): SculkRepository<T, ID> =
        repositories.getOrPut(serializer.descriptor.serialName) {
            JdbcRepository<T, ID>(dataSource, serializer, dialect, logger).also { it.migrate() }
        } as SculkRepository<T, ID>

    /**
     * Runs [block] inside one transaction, rolling back if it throws.
     *
     * Public because a plugin moving money between two accounts needs both writes to land or
     * neither, and no repository method can express that across two tables.
     */
    @SculkStable
    public suspend fun <T> transaction(block: (Connection) -> T): SculkResult<T> = withContext(Dispatchers.IO) {
        SculkResult.catching("run a transaction") {
            dataSource.connection.use { connection ->
                val previous = connection.autoCommit
                connection.autoCommit = false
                try {
                    val result = block(connection)
                    connection.commit()
                    result
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                } finally {
                    connection.autoCommit = previous
                }
            }
        }
    }

    override fun close() {
        if (owned) (dataSource as? HikariDataSource)?.close()
    }

    public companion object {
        /** Opens the backend described by [settings]. */
        @SculkInternal
        public fun open(settings: StorageSettings, dataFolder: File, logger: Logger): SculkResult<SculkData> {
            val dialect = runCatching { SqlDialect.of(settings.backend) }.getOrElse {
                return SculkResult.failure("storage.yml names an unknown backend '${settings.backend}'.", it)
            }

            return SculkResult.catching("open the ${settings.backend} database") {
                val config = HikariConfig().apply {
                    jdbcUrl = urlFor(dialect, settings, dataFolder)
                    // Naming the driver is not optional however redundant it looks. Paper loads a
                    // plugin's libraries into an isolated classloader, and DriverManager discovers
                    // drivers through a ServiceLoader that cannot see into it — so a driver that
                    // is present and downloaded still yields "No suitable driver". Given the class
                    // name, Hikari instantiates it directly and skips DriverManager entirely.
                    driverClassName = driverFor(dialect)
                    // SQLite is a single file with a single writer; more connections only produce
                    // lock contention.
                    maximumPoolSize = if (dialect == SqlDialect.SQLITE) 1 else settings.poolSize
                    if (dialect != SqlDialect.SQLITE) {
                        username = settings.remote.username
                        password = settings.remote.password
                    }
                    poolName = "sculk-data"
                }

                val source = HikariDataSource(config)
                // Taking a connection now means a wrong password fails at boot, next to the config
                // that caused it, rather than on the first player login twenty minutes later.
                source.connection.use { require(it.isValid(5)) { "the database did not answer" } }
                SculkData(source, dialect, logger)
            }
        }

        /** Wraps an existing [dataSource]. For tests, and for plugins that own their own pool. */
        @SculkInternal
        public fun using(dataSource: DataSource, dialect: SqlDialect, logger: Logger): SculkData =
            SculkData(dataSource, dialect, logger, owned = false)

        @SculkInternal
        internal fun urlFor(dialect: SqlDialect, settings: StorageSettings, dataFolder: File): String {
            val remote = settings.remote
            return when (dialect) {
                SqlDialect.SQLITE -> "jdbc:sqlite:${File(dataFolder, settings.file).absolutePath}"

                // `allowPublicKeyRetrieval` when TLS is off, or a stock MySQL 8 cannot be connected to
                // at all. MySQL 8 authenticates with `caching_sha2_password` by default, and that
                // exchange needs either TLS or permission to fetch the server's public key — without
                // one of them the driver fails with "RSA public key is not available client side",
                // which reads as nothing to do with the config and happens before a single statement
                // is sent. MariaDB servers are unaffected either way; the flag is ignored there.
                //
                // The MITM caveat is real but narrow: it applies to the initial handshake on a network
                // where someone can impersonate the database, and these connections are overwhelmingly
                // localhost or a private network. Someone exposing their database to the internet
                // should set `use-ssl: true`, which drops this flag and is the right answer anyway.
                SqlDialect.MYSQL ->
                    buildString {
                        append("jdbc:mariadb://${remote.host}:${remote.port}/${remote.database}")
                        append("?useSSL=${remote.useSsl}")
                        if (!remote.useSsl) append("&allowPublicKeyRetrieval=true")
                    }

                SqlDialect.POSTGRES ->
                    "jdbc:postgresql://${remote.host}:${remote.port}/${remote.database}?ssl=${remote.useSsl}"
            }
        }

        private fun driverFor(dialect: SqlDialect): String = when (dialect) {
            SqlDialect.SQLITE -> "org.sqlite.JDBC"
            SqlDialect.MYSQL -> "org.mariadb.jdbc.Driver"
            SqlDialect.POSTGRES -> "org.postgresql.Driver"
        }
    }
}
