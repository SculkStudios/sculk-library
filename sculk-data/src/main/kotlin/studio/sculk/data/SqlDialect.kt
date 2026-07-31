package studio.sculk.data

import studio.sculk.annotation.SculkStable

/**
 * The parts of SQL the supported engines disagree about.
 *
 * Everything here exists because a generated statement was wrong on at least one backend.
 */
@SculkStable
public enum class SqlDialect {
    SQLITE,
    MYSQL,
    POSTGRES,
    ;

    /**
     * Quotes an identifier.
     *
     * Not optional. An entity with a column called `key`, `order` or `group` — all ordinary words
     * for a plugin to use — produced a syntax error on every backend without this, and the
     * previous dialect did no quoting at all.
     */
    public fun quote(identifier: String): String = when (this) {
        MYSQL -> "`$identifier`"
        SQLITE, POSTGRES -> "\"$identifier\""
    }

    /** The column type for [type] on this engine. */
    public fun columnType(type: SqlType): String = when (this) {
        SQLITE -> when (type) {
            SqlType.INTEGER, SqlType.BIGINT -> "INTEGER"

            SqlType.DOUBLE -> "REAL"

            // SQLite has no boolean; it stores 0/1 in an INTEGER and the driver converts.
            SqlType.BOOLEAN -> "INTEGER"

            SqlType.TEXT, SqlType.JSON -> "TEXT"
        }

        MYSQL -> when (type) {
            SqlType.INTEGER -> "INT"

            SqlType.BIGINT -> "BIGINT"

            SqlType.DOUBLE -> "DOUBLE"

            SqlType.BOOLEAN -> "TINYINT(1)"

            // VARCHAR(255) would be enough for most keys and silently truncate the rest.
            SqlType.TEXT -> "VARCHAR(255)"

            SqlType.JSON -> "TEXT"
        }

        POSTGRES -> when (type) {
            SqlType.INTEGER -> "INTEGER"
            SqlType.BIGINT -> "BIGINT"
            SqlType.DOUBLE -> "DOUBLE PRECISION"
            SqlType.BOOLEAN -> "BOOLEAN"
            SqlType.TEXT, SqlType.JSON -> "TEXT"
        }
    }

    /**
     * An insert-or-update that leaves columns it does not mention alone.
     *
     * The previous implementation used `REPLACE INTO` on MySQL. That is a delete followed by an
     * insert: it wipes any column absent from the statement, fires `ON DELETE` cascades against
     * rows nobody asked to delete, and burns an auto-increment value every save. `INSERT … ON
     * DUPLICATE KEY UPDATE` updates in place.
     *
     * A row whose only column is its key has nothing to update, so the statement becomes a
     * no-op-on-conflict rather than invalid SQL.
     */
    public fun upsert(table: String, columns: List<String>, keyColumn: String): String {
        val quotedTable = quote(table)
        val quotedColumns = columns.joinToString(", ") { quote(it) }
        val placeholders = columns.joinToString(", ") { "?" }
        val updatable = columns.filter { it != keyColumn }

        return when (this) {
            MYSQL -> {
                val assignments = updatable.joinToString(", ") { "${quote(it)} = VALUES(${quote(it)})" }
                val tail = if (assignments.isEmpty()) {
                    "ON DUPLICATE KEY UPDATE ${quote(keyColumn)} = ${quote(keyColumn)}"
                } else {
                    "ON DUPLICATE KEY UPDATE $assignments"
                }
                "INSERT INTO $quotedTable ($quotedColumns) VALUES ($placeholders) $tail"
            }

            SQLITE, POSTGRES -> {
                val assignments = updatable.joinToString(", ") { "${quote(it)} = excluded.${quote(it)}" }
                val tail = if (assignments.isEmpty()) {
                    "ON CONFLICT (${quote(keyColumn)}) DO NOTHING"
                } else {
                    "ON CONFLICT (${quote(keyColumn)}) DO UPDATE SET $assignments"
                }
                "INSERT INTO $quotedTable ($quotedColumns) VALUES ($placeholders) $tail"
            }
        }
    }

    public companion object {
        @SculkStable
        public fun of(name: String): SqlDialect = when (name.lowercase().trim()) {
            "sqlite" -> SQLITE
            "mysql", "mariadb" -> MYSQL
            "postgres", "postgresql" -> POSTGRES
            else -> error("Unknown storage backend '$name'; expected sqlite, mysql or postgres.")
        }
    }
}
