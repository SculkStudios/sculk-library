package gg.sculk.data.repository

import gg.sculk.core.SculkResult
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.data.driver.SqlDialect
import gg.sculk.data.orm.OrmMapper
import gg.sculk.data.orm.OrmMapping
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * JDBC-backed implementation of [SculkRepository].
 *
 * Table is created on construction. All operations use the [DataSource] for connection management.
 *
 * Upsert strategy:
 * - SQLite: `INSERT OR REPLACE INTO ...`
 * - MySQL/MariaDB: `REPLACE INTO ...` (delete-then-insert semantics, no double-binding required)
 */
@SculkInternal
public class JdbcRepository<T : Any, ID : Any>(
    private val dataSource: DataSource,
    private val mapping: OrmMapping<T>,
    private val dialect: SqlDialect,
) : SculkRepository<T, ID> {
    init {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(mapping.createTableSql)
            }
        }
    }

    override fun find(id: ID): SculkResult<T?> =
        runCatching {
            dataSource.connection.use { conn ->
                val sql = "SELECT * FROM ${mapping.tableName} WHERE ${mapping.primaryKey.columnName} = ?"
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, OrmMapper.coerceToSqlPublic(id))
                    val rs = ps.executeQuery()
                    if (rs.next()) OrmMapper.fromResultSet(rs, mapping) else null
                }
            }
        }.fold(
            onSuccess = { SculkResult.success(it) },
            onFailure = { SculkResult.failure("find failed: ${it.message}", it) },
        )

    override fun findAll(): SculkResult<List<T>> =
        runCatching {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM ${mapping.tableName}").use { ps ->
                    val rs = ps.executeQuery()
                    buildList {
                        while (rs.next()) add(OrmMapper.fromResultSet(rs, mapping))
                    }
                }
            }
        }.fold(
            onSuccess = { SculkResult.success(it) },
            onFailure = { SculkResult.failure("findAll failed: ${it.message}", it) },
        )

    override fun save(entity: T): SculkResult<Unit> =
        runCatching {
            dataSource.connection.use { conn ->
                conn.prepareStatement(upsertSql()).use { ps ->
                    OrmMapper
                        .valuesOf(entity, mapping)
                        .forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                    ps.executeUpdate()
                }
            }
        }.fold(
            onSuccess = { SculkResult.success(Unit) },
            onFailure = { SculkResult.failure("save failed: ${it.message}", it) },
        )

    override fun delete(id: ID): SculkResult<Unit> =
        runCatching {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM ${mapping.tableName} WHERE ${mapping.primaryKey.columnName} = ?"
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, OrmMapper.coerceToSqlPublic(id))
                    ps.executeUpdate()
                }
            }
        }.fold(
            onSuccess = { SculkResult.success(Unit) },
            onFailure = { SculkResult.failure("delete failed: ${it.message}", it) },
        )

    override fun exists(id: ID): SculkResult<Boolean> =
        runCatching {
            dataSource.connection.use { conn ->
                val sql =
                    "SELECT COUNT(*) FROM ${mapping.tableName} WHERE ${mapping.primaryKey.columnName} = ?"
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, OrmMapper.coerceToSqlPublic(id))
                    val rs = ps.executeQuery()
                    rs.next() && rs.getInt(1) > 0
                }
            }
        }.fold(
            onSuccess = { SculkResult.success(it) },
            onFailure = { SculkResult.failure("exists failed: ${it.message}", it) },
        )

    /**
     * Upsert SQL — single parameter set for all columns.
     * Both dialects use delete-then-insert semantics when a primary key conflict occurs.
     */
    private fun upsertSql(): String {
        val cols = mapping.columns.joinToString(", ") { it.columnName }
        val placeholders = mapping.columns.joinToString(", ") { "?" }
        return when (dialect) {
            SqlDialect.SQLITE -> "INSERT OR REPLACE INTO ${mapping.tableName} ($cols) VALUES ($placeholders)"
            SqlDialect.MYSQL -> "REPLACE INTO ${mapping.tableName} ($cols) VALUES ($placeholders)"
        }
    }
}

/** Creates a [JdbcRepository] for entity class [T] with primary key type [ID]. */
@SculkInternal
public fun <T : Any, ID : Any> jdbcRepository(
    dataSource: DataSource,
    klass: KClass<T>,
    dialect: SqlDialect,
): JdbcRepository<T, ID> = JdbcRepository(dataSource, OrmMapper.mappingFor(klass), dialect)
