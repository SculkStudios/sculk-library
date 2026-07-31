package studio.sculk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.logging.Logger
import javax.sql.DataSource

/** The JDBC-backed [SculkRepository]. */
@SculkInternal
public class JdbcRepository<T : Any, ID : Any>(
    private val dataSource: DataSource,
    private val serializer: KSerializer<T>,
    private val dialect: SqlDialect,
    private val logger: Logger,
) : SculkRepository<T, ID> {
    private val schema = TableSchema.of(serializer.descriptor)

    override val table: String get() = schema.table

    internal fun migrate() {
        dataSource.connection.use { SchemaMigrator.apply(it, schema, dialect, logger) }
    }

    /**
     * Runs [block] off the caller's thread and reports failure both ways.
     *
     * **Every failure is logged before it is returned.** Callers overwhelmingly ignore the result
     * of a write — "my data does not save and there is nothing in the log" is the bug that costs
     * the most to diagnose and the least to prevent.
     */
    private suspend fun <R> io(what: String, block: (Connection) -> R): SculkResult<R> = withContext(Dispatchers.IO) {
        try {
            SculkResult.success(dataSource.connection.use(block))
        } catch (error: Exception) {
            logger.warning("[SculkData] Failed to $what on ${schema.table}: ${error.message}")
            SculkResult.failure("Failed to $what on ${schema.table}: ${error.message}", error)
        }
    }

    override suspend fun find(id: ID): SculkResult<T?> = io("find $id") { connection ->
        val sql = "SELECT * FROM ${dialect.quote(schema.table)} WHERE ${dialect.quote(schema.key.name)} = ? LIMIT 1"
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
        }
    }

    override suspend fun findAll(): SculkResult<List<T>> = io("read every row") { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM ${dialect.quote(schema.table)}").use { rows -> readAll(rows) }
        }
    }

    override suspend fun query(query: Query.() -> Unit): SculkResult<List<T>> = io("run a query") { connection ->
        val built = Query().apply(query)
        connection.prepareStatement(selectSql(built)).use { statement ->
            bind(statement, parametersOf(built))
            statement.executeQuery().use { rows -> readAll(rows) }
        }
    }

    override suspend fun findFirst(query: Query.() -> Unit): SculkResult<T?> = io("find the first match") { connection ->
        val built = Query().apply(query).also { it.take(1) }
        connection.prepareStatement(selectSql(built)).use { statement ->
            bind(statement, parametersOf(built))
            statement.executeQuery().use { rows -> if (rows.next()) read(rows) else null }
        }
    }

    override suspend fun count(query: Query.() -> Unit): SculkResult<Long> = io("count rows") { connection ->
        val built = Query().apply(query)
        val where = built.condition?.render(dialect)
        val sql = "SELECT COUNT(*) FROM ${dialect.quote(schema.table)}" + (where?.let { " WHERE ${it.first}" } ?: "")
        connection.prepareStatement(sql).use { statement ->
            bind(statement, where?.second.orEmpty())
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
        }
    }

    override suspend fun exists(id: ID): SculkResult<Boolean> = io("check for $id") { connection ->
        val sql = "SELECT 1 FROM ${dialect.quote(schema.table)} WHERE ${dialect.quote(schema.key.name)} = ? LIMIT 1"
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { it.next() }
        }
    }

    override suspend fun save(value: T): SculkResult<Unit> = io("save a row") { connection ->
        connection.prepareStatement(upsertSql()).use { statement ->
            bind(statement, RowCodec.encode(serializer, value, schema).let { row -> schema.columnNames.map { row[it] } })
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun saveAll(values: Collection<T>): SculkResult<Unit> = io("save ${values.size} rows") { connection ->
        if (values.isEmpty()) return@io Unit
        // One transaction and one batch: a hundred separate commits is a hundred fsyncs.
        transaction(connection) {
            connection.prepareStatement(upsertSql()).use { statement ->
                for (value in values) {
                    val row = RowCodec.encode(serializer, value, schema)
                    bind(statement, schema.columnNames.map { row[it] })
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
        Unit
    }

    override suspend fun delete(id: ID): SculkResult<Unit> = io("delete $id") { connection ->
        val sql = "DELETE FROM ${dialect.quote(schema.table)} WHERE ${dialect.quote(schema.key.name)} = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, id)
            statement.executeUpdate()
        }
        Unit
    }

    override suspend fun deleteWhere(query: Query.() -> Unit): SculkResult<Int> = io("delete matching rows") { connection ->
        val built = Query().apply(query)
        val where = built.condition?.render(dialect)
        val sql = "DELETE FROM ${dialect.quote(schema.table)}" + (where?.let { " WHERE ${it.first}" } ?: "")
        connection.prepareStatement(sql).use { statement ->
            bind(statement, where?.second.orEmpty())
            statement.executeUpdate()
        }
    }

    override suspend fun topBy(column: String, rows: Int, ascending: Boolean): SculkResult<List<T>> =
        io("read the top $rows by $column") { connection ->
            require(schema[column] != null) { "No column named '$column' on ${schema.table}." }
            val direction = if (ascending) "ASC" else "DESC"
            val sql = "SELECT * FROM ${dialect.quote(schema.table)} ORDER BY ${dialect.quote(column)} $direction LIMIT ?"
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, rows)
                statement.executeQuery().use { readAll(it) }
            }
        }

    private fun upsertSql() = dialect.upsert(schema.table, schema.columnNames, schema.key.name)

    private fun selectSql(query: Query): String = buildString {
        append("SELECT * FROM ").append(dialect.quote(schema.table))
        query.condition?.let { append(" WHERE ").append(it.render(dialect).first) }
        if (query.orderBy.isNotEmpty()) {
            append(" ORDER BY ")
            append(
                query.orderBy.joinToString(", ") { (column, ascending) ->
                    "${dialect.quote(column)} ${if (ascending) "ASC" else "DESC"}"
                },
            )
        }
        query.limit?.let { append(" LIMIT ").append(it) }
        // Every supported engine requires a LIMIT before OFFSET; -1 means "no cap" on SQLite and
        // MySQL, and Postgres accepts OFFSET alone, so the guard costs nothing and fixes two.
        query.offset?.let {
            if (query.limit == null) append(" LIMIT -1")
            append(" OFFSET ").append(it)
        }
    }

    private fun parametersOf(query: Query): List<Any?> = query.condition?.render(dialect)?.second.orEmpty()

    private fun bind(statement: PreparedStatement, parameters: List<Any?>) {
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    }

    private fun read(rows: ResultSet): T {
        val row = schema.columnNames.associateWith { rows.getObject(it) }
        return RowCodec.decode(serializer, row, schema)
    }

    private fun readAll(rows: ResultSet): List<T> {
        val all = mutableListOf<T>()
        while (rows.next()) all += read(rows)
        return all
    }

    private inline fun transaction(connection: Connection, block: () -> Unit) {
        val previous = connection.autoCommit
        connection.autoCommit = false
        try {
            block()
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previous
        }
    }
}
