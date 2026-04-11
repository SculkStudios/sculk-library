package gg.sculk.data.driver

import gg.sculk.config.annotation.ConfigFile
import gg.sculk.config.annotation.NotEmpty
import gg.sculk.core.annotation.SculkStable

/**
 * Auto-generated `storage.yml` configuration.
 *
 * Generated on first plugin start if the file does not exist:
 * ```yaml
 * type: sqlite
 * sqlite:
 *   file: data.db
 * mysql:
 *   host: localhost
 *   port: 3306
 *   database: sculk
 *   username: root
 *   password: ""
 * ```
 */
@SculkStable
@ConfigFile("storage.yml")
public data class StorageConfig(
    @NotEmpty val type: String = "sqlite",
    val sqlite: SqliteConfig = SqliteConfig(),
    val mysql: MysqlConfig = MysqlConfig(),
) {
    /** Returns the [SqlDialect] matching [type]. Defaults to [SqlDialect.SQLITE] for unknown values. */
    public fun dialect(): SqlDialect =
        when (type.lowercase()) {
            "mysql", "mariadb" -> SqlDialect.MYSQL
            else -> SqlDialect.SQLITE
        }
}

/** SQLite-specific connection settings. */
@SculkStable
public data class SqliteConfig(
    val file: String = "data.db",
)

/** MySQL / MariaDB connection settings. */
@SculkStable
public data class MysqlConfig(
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "sculk",
    val username: String = "root",
    val password: String = "",
    val poolSize: Int = 10,
)
