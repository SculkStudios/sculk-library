package studio.sculk.data

import java.sql.Connection
import java.util.logging.Logger

/**
 * Brings a table up to the shape its entity declares — additively, and only additively.
 *
 * Columns are created and indexed. Nothing is ever dropped or retyped: a migration that removes a
 * column is indistinguishable from a rollback that has not finished, and guessing wrong destroys
 * data that no plugin can restore. A removed field simply leaves its column behind.
 */
internal object SchemaMigrator {
    fun apply(connection: Connection, schema: TableSchema, dialect: SqlDialect, logger: Logger) {
        createTable(connection, schema, dialect)
        val existing = existingColumns(connection, schema, dialect)
        addMissingColumns(connection, schema, dialect, existing, logger)
        createIndexes(connection, schema, dialect)
    }

    private fun createTable(connection: Connection, schema: TableSchema, dialect: SqlDialect) {
        val columns = schema.columns.joinToString(", ") { column ->
            buildString {
                append(dialect.quote(column.name)).append(' ').append(dialect.columnType(column.type))
                if (column.primaryKey) append(" PRIMARY KEY")
                if (!column.nullable && !column.primaryKey) append(" NOT NULL")
            }
        }
        connection.createStatement().use {
            it.executeUpdate("CREATE TABLE IF NOT EXISTS ${dialect.quote(schema.table)} ($columns)")
        }
    }

    /**
     * The columns the table already has, read through JDBC metadata.
     *
     * Deliberately not `information_schema`: the filter that scopes it to the current database
     * differs per engine, and getting it wrong returns *nothing* rather than an error — which
     * looks exactly like an empty table and makes the migrator try to add every column again. The
     * driver already knows how to answer this.
     *
     * The name is retried in three cases because engines store identifiers differently: H2 folds
     * to upper by default, MySQL on Linux keeps the case it was given, and SQLite preserves it.
     */
    private fun existingColumns(connection: Connection, schema: TableSchema, dialect: SqlDialect): Set<String> {
        for (candidate in listOf(schema.table, schema.table.uppercase(), schema.table.lowercase()).distinct()) {
            val found = mutableSetOf<String>()
            connection.metaData.getColumns(null, null, candidate, null).use { rows ->
                while (rows.next()) found += rows.getString("COLUMN_NAME")
            }
            if (found.isNotEmpty()) return found
        }
        return emptySet()
    }

    private fun addMissingColumns(
        connection: Connection,
        schema: TableSchema,
        dialect: SqlDialect,
        existing: Set<String>,
        logger: Logger,
    ) {
        for (column in schema.columns) {
            if (existing.any { it.equals(column.name, ignoreCase = true) }) continue
            // Always nullable: rows already in the table have no value for it, and the entity's
            // Kotlin default fills the gap on read.
            val sql = "ALTER TABLE ${dialect.quote(schema.table)} ADD COLUMN " +
                "${dialect.quote(column.name)} ${dialect.columnType(column.type)}"
            connection.createStatement().use { it.executeUpdate(sql) }
            logger.info("[SculkData] Added ${schema.table}.${column.name}.")
        }
    }

    private fun createIndexes(connection: Connection, schema: TableSchema, dialect: SqlDialect) {
        for (column in schema.columns.filter { it.indexed && !it.primaryKey }) {
            // Created separately from the table so an @Index added in a later version still
            // appears on a table that already exists.
            val name = "idx_${schema.table}_${column.name}"
            val sql = "CREATE INDEX IF NOT EXISTS ${dialect.quote(name)} " +
                "ON ${dialect.quote(schema.table)} (${dialect.quote(column.name)})"
            connection.createStatement().use { it.executeUpdate(sql) }
        }
    }
}
