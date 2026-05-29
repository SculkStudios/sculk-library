package studio.sculk.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.sculk.core.SculkResult
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.data.driver.SqlDialect
import studio.sculk.data.orm.OrmMapper
import studio.sculk.data.orm.OrmMapping
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * JDBC-backed implementation of [SculkRepository].
 *
 * The table is created on construction. All operations are suspend functions that run their
 * blocking JDBC work on [Dispatchers.IO], so they never stall the calling coroutine's thread.
 *
 * Upsert strategy:
 * - SQLite: `INSERT OR REPLACE INTO ...`
 * - MySQL/MariaDB: `REPLACE INTO ...`
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

    override suspend fun find(id: ID): SculkResult<T?> =
        io("find") {
            dataSource.connection.use { conn ->
                val sql = "SELECT * FROM ${mapping.tableName} WHERE ${mapping.primaryKey.columnName} = ?"
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, OrmMapper.coerceToSqlPublic(id))
                    val rs = ps.executeQuery()
                    if (rs.next()) OrmMapper.fromResultSet(rs, mapping) else null
                }
            }
        }

    override suspend fun findAll(): SculkResult<List<T>> =
        io("findAll") {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM ${mapping.tableName}").use { ps ->
                    val rs = ps.executeQuery()
                    buildList { while (rs.next()) add(OrmMapper.fromResultSet(rs, mapping)) }
                }
            }
        }

    override suspend fun save(entity: T): SculkResult<Unit> =
        io("save") {
            dataSource.connection.use { conn ->
                conn.prepareStatement(upsertSql()).use { ps ->
                    OrmMapper.valuesOf(entity, mapping).forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                    ps.executeUpdate()
                }
            }
        }

    override suspend fun delete(id: ID): SculkResult<Unit> =
        io("delete") {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM ${mapping.tableName} WHERE ${mapping.primaryKey.columnName} = ?"
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, OrmMapper.coerceToSqlPublic(id))
                    ps.executeUpdate()
                }
            }
        }

    override suspend fun exists(id: ID): SculkResult<Boolean> =
        io("exists") {
            dataSource.connection.use { conn ->
                val sql = "SELECT COUNT(*) FROM ${mapping.tableName} WHERE ${mapping.primaryKey.columnName} = ?"
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, OrmMapper.coerceToSqlPublic(id))
                    val rs = ps.executeQuery()
                    rs.next() && rs.getInt(1) > 0
                }
            }
        }

    override suspend fun saveAll(entities: List<T>): SculkResult<Unit> {
        if (entities.isEmpty()) return SculkResult.success(Unit)
        return io("saveAll") {
            dataSource.connection.use { conn ->
                val previousAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    conn.prepareStatement(upsertSql()).use { ps ->
                        for (entity in entities) {
                            OrmMapper.valuesOf(entity, mapping).forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = previousAutoCommit
                }
            }
        }
    }

    override suspend fun query(block: QueryBuilder<T>.() -> Unit): SculkResult<List<T>> {
        val builder = QueryBuilder<T>().apply(block)
        return io("query") {
            val sql = StringBuilder("SELECT * FROM ${mapping.tableName}")
            val bindings = mutableListOf<Any?>()
            if (builder.conditions.isNotEmpty()) {
                sql.append(" WHERE ")
                sql.append(
                    builder.conditions.joinToString(" AND ") { condition ->
                        "${columnFor(condition.property)} ${condition.operator} ?"
                    },
                )
                builder.conditions.forEach { bindings += OrmMapper.coerceToSqlPublic(it.value) }
            }
            builder.orderProperty?.let { property ->
                sql.append(" ORDER BY ${columnFor(property)}")
                if (builder.orderDescending) sql.append(" DESC")
            }
            builder.limitValue?.let { sql.append(" LIMIT $it") }

            dataSource.connection.use { conn ->
                conn.prepareStatement(sql.toString()).use { ps ->
                    bindings.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                    val rs = ps.executeQuery()
                    buildList { while (rs.next()) add(OrmMapper.fromResultSet(rs, mapping)) }
                }
            }
        }
    }

    private fun columnFor(property: String): String =
        mapping.columns.firstOrNull { it.paramName == property }?.columnName
            ?: throw IllegalArgumentException("Unknown property '$property' on ${mapping.tableName}.")

    private fun upsertSql(): String {
        val cols = mapping.columns.joinToString(", ") { it.columnName }
        val placeholders = mapping.columns.joinToString(", ") { "?" }
        return when (dialect) {
            SqlDialect.SQLITE -> "INSERT OR REPLACE INTO ${mapping.tableName} ($cols) VALUES ($placeholders)"
            SqlDialect.MYSQL -> "REPLACE INTO ${mapping.tableName} ($cols) VALUES ($placeholders)"
        }
    }

    private suspend inline fun <R> io(
        operation: String,
        crossinline body: () -> R,
    ): SculkResult<R> =
        withContext(Dispatchers.IO) {
            runCatching { body() }.fold(
                onSuccess = { SculkResult.success(it) },
                onFailure = { SculkResult.failure("$operation failed: ${it.message}", it) },
            )
        }
}

/** Creates a [JdbcRepository] for entity class [T] with primary key type [ID]. */
@SculkInternal
public fun <T : Any, ID : Any> jdbcRepository(
    dataSource: DataSource,
    klass: KClass<T>,
    dialect: SqlDialect,
): JdbcRepository<T, ID> = JdbcRepository(dataSource, OrmMapper.mappingFor(klass), dialect)
