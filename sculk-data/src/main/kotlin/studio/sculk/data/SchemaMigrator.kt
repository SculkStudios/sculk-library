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
        val added = addMissingColumns(connection, schema, dialect, existing, logger)
        warnOnAbandonedColumns(schema, existing, added, logger)
        createIndexes(connection, schema, dialect)
    }

    /**
     * Warns when new columns appear *beside* columns this entity no longer models.
     *
     * That pair is the signature of a rename, and a rename is the one shape additive migration
     * handles badly: the old column keeps every row's data, the new one is empty, and each row then
     * reads back as its Kotlin defaults. Nothing throws, nothing is dropped, and a plugin whose
     * balances are all zero looks like it lost the table rather than like it is reading the wrong
     * column. 5.0 derives column names from the property verbatim where 4.5 converted them to
     * snake_case, so every 4.5 table hits this on first boot.
     *
     * A warning rather than a refusal, because the same shape is also produced by a legitimately
     * removed field, and refusing to boot over a column somebody deleted on purpose is worse.
     */
    private fun warnOnAbandonedColumns(schema: TableSchema, existing: Set<String>, added: List<String>, logger: Logger) {
        if (added.isEmpty() || existing.isEmpty()) return
        val unmodelled = existing.filterNot { column -> schema.columns.any { it.name.equals(column, ignoreCase = true) } }
        if (unmodelled.isEmpty()) return

        logger.warning(
            "[SculkData] ${schema.table}: added ${added.sorted()} to a table that already has " +
                "${unmodelled.sorted()}, which this entity does not model. If those are the same fields under an " +
                "older name, their data is still in the old columns and every row will read back as its Kotlin " +
                "default. Nothing here drops data, but nothing here moves it either — rename the columns in SQL " +
                "before using the table.",
        )
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

    /** Returns the columns it added, so [warnOnAbandonedColumns] can tell a rename from a new field. */
    private fun addMissingColumns(
        connection: Connection,
        schema: TableSchema,
        dialect: SqlDialect,
        existing: Set<String>,
        logger: Logger,
    ): List<String> {
        val added = mutableListOf<String>()
        for (column in schema.columns) {
            if (existing.any { it.equals(column.name, ignoreCase = true) }) continue
            // Always nullable: rows already in the table have no value for it, and the entity's
            // Kotlin default fills the gap on read.
            val sql = "ALTER TABLE ${dialect.quote(schema.table)} ADD COLUMN " +
                "${dialect.quote(column.name)} ${dialect.columnType(column.type)}"
            connection.createStatement().use { it.executeUpdate(sql) }
            logger.info("[SculkData] Added ${schema.table}.${column.name}.")
            added += column.name
        }
        return added
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
