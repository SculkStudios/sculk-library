package studio.sculk.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlDialectTest {
    @Test
    fun `identifiers are quoted per engine`() {
        assertEquals("`order`", SqlDialect.MYSQL.quote("order"))
        assertEquals("\"order\"", SqlDialect.SQLITE.quote("order"))
        assertEquals("\"order\"", SqlDialect.POSTGRES.quote("order"))
    }

    @Test
    fun `the mysql upsert updates in place rather than replacing the row`() {
        val sql = SqlDialect.MYSQL.upsert("players", listOf("id", "name", "coins"), "id")

        // REPLACE INTO is a delete followed by an insert: it drops columns not named here, fires
        // ON DELETE cascades, and burns an auto-increment value on every save.
        assertTrue(!sql.contains("REPLACE", ignoreCase = true), "REPLACE INTO must never be generated: $sql")
        assertEquals(
            "INSERT INTO `players` (`id`, `name`, `coins`) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `coins` = VALUES(`coins`)",
            sql,
        )
    }

    @Test
    fun `the sqlite and postgres upserts use on conflict do update`() {
        val expected = { q: (String) -> String ->
            "INSERT INTO ${q("players")} (${q("id")}, ${q("name")}) VALUES (?, ?) " +
                "ON CONFLICT (${q("id")}) DO UPDATE SET ${q("name")} = excluded.${q("name")}"
        }

        assertEquals(expected { "\"$it\"" }, SqlDialect.SQLITE.upsert("players", listOf("id", "name"), "id"))
        assertEquals(expected { "\"$it\"" }, SqlDialect.POSTGRES.upsert("players", listOf("id", "name"), "id"))
    }

    @Test
    fun `a key-only table produces a valid no-op upsert rather than a dangling SET`() {
        assertTrue(SqlDialect.SQLITE.upsert("t", listOf("id"), "id").endsWith("DO NOTHING"))
        assertTrue(SqlDialect.MYSQL.upsert("t", listOf("id"), "id").endsWith("`id` = `id`"))
    }

    @Test
    fun `column types differ where the engines do`() {
        assertEquals("INTEGER", SqlDialect.SQLITE.columnType(SqlType.BOOLEAN), "sqlite has no boolean")
        assertEquals("TINYINT(1)", SqlDialect.MYSQL.columnType(SqlType.BOOLEAN))
        assertEquals("BOOLEAN", SqlDialect.POSTGRES.columnType(SqlType.BOOLEAN))
        assertEquals("REAL", SqlDialect.SQLITE.columnType(SqlType.DOUBLE))
        assertEquals("DOUBLE PRECISION", SqlDialect.POSTGRES.columnType(SqlType.DOUBLE))
    }

    @Test
    fun `a backend name resolves case-insensitively and mariadb means mysql`() {
        assertEquals(SqlDialect.MYSQL, SqlDialect.of("MySQL"))
        assertEquals(SqlDialect.MYSQL, SqlDialect.of("mariadb"))
        assertEquals(SqlDialect.POSTGRES, SqlDialect.of("postgresql"))
        assertEquals(SqlDialect.SQLITE, SqlDialect.of(" sqlite "))
    }

    @Test
    fun `an unknown backend names what was expected`() {
        val failure = runCatching { SqlDialect.of("mongo") }.exceptionOrNull()

        assertTrue(failure!!.message!!.contains("sqlite, mysql or postgres"))
    }
}
