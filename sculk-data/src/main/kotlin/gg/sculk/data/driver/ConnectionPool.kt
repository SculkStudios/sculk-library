package gg.sculk.data.driver

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import gg.sculk.core.annotation.SculkInternal
import java.io.File

/**
 * Builds a [HikariDataSource] from a [StorageConfig].
 *
 * Closed when [SculkData][gg.sculk.data.SculkData] shuts down.
 */
@SculkInternal
public object ConnectionPool {
    /**
     * Creates a [HikariDataSource] for [config].
     *
     * [dataFolder] is used to resolve the SQLite file path relative to the plugin's data directory.
     */
    public fun create(
        config: StorageConfig,
        dataFolder: File,
    ): HikariDataSource {
        val hikari = HikariConfig()
        when (config.dialect()) {
            SqlDialect.SQLITE -> {
                val dbFile = File(dataFolder, config.sqlite.file)
                dbFile.parentFile?.mkdirs()
                hikari.driverClassName = "org.sqlite.JDBC"
                hikari.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                hikari.maximumPoolSize = 1 // SQLite is single-writer
                hikari.connectionTestQuery = "SELECT 1"
            }
            SqlDialect.MYSQL -> {
                val mysql = config.mysql
                hikari.driverClassName = "org.mariadb.jdbc.Driver"
                hikari.jdbcUrl =
                    "jdbc:mariadb://${mysql.host}:${mysql.port}/${mysql.database}"
                hikari.username = mysql.username
                hikari.password = mysql.password
                hikari.maximumPoolSize = mysql.poolSize
            }
        }
        hikari.poolName = "SculkData"
        hikari.isAutoCommit = true
        return HikariDataSource(hikari)
    }
}
