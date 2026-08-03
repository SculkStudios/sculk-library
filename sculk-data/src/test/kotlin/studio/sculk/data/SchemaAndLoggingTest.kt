package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

@OptIn(SculkInternal::class)
class SchemaAndLoggingTest {
    private fun h2() = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DATABASE_TO_LOWER=TRUE"
            driverClassName = "org.h2.Driver"
        },
    )

    private class Capture : Handler() {
        val records = mutableListOf<String>()

        override fun publish(record: LogRecord) {
            records += record.message
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    private fun capturingLogger(capture: Capture) = Logger.getLogger("capture-${UUID.randomUUID()}").apply {
        useParentHandlers = false
        addHandler(capture)
    }

    @Test
    fun `an entity with no primary key fails loudly at startup`() {
        val failure = runCatching { TableSchema.of(serializer<NoKeyRow>().descriptor) }.exceptionOrNull()

        assertTrue(failure!!.message!!.contains("exactly one @Id"), failure.message)
        assertTrue(failure.message!!.contains("found 0"))
    }

    @Test
    fun `an entity with two primary keys fails loudly at startup`() {
        val failure = runCatching { TableSchema.of(serializer<TwoKeyRow>().descriptor) }.exceptionOrNull()

        assertTrue(failure!!.message!!.contains("found 2"), failure.message)
    }

    @Test
    fun `the schema maps kotlin types onto sql types`() {
        val schema = TableSchema.of(serializer<PlayerRow>().descriptor)

        assertEquals("players", schema.table)
        assertEquals("id", schema.key.name)
        assertEquals(SqlType.BIGINT, schema["coins"]!!.type)
        assertEquals(SqlType.INTEGER, schema["level"]!!.type)
        assertEquals(SqlType.DOUBLE, schema["ratio"]!!.type)
        assertEquals(SqlType.BOOLEAN, schema["banned"]!!.type)
        assertEquals(SqlType.TEXT, schema["rank"]!!.type, "enums are stored by name")
        assertEquals(SqlType.JSON, schema["homes"]!!.type)
        assertTrue(schema["note"]!!.nullable)
        assertTrue(schema["name"]!!.indexed)
    }

    @Test
    fun `an indexed column gets an index`() = runTest {
        val source = h2()
        SculkData.using(source, SqlDialect.MYSQL, Logger.getLogger("t")).repository<PlayerRow, String>()

        val indexes = mutableListOf<String>()
        source.connection.use { connection ->
            connection.metaData.getIndexInfo(null, null, "players", false, false).use { rows ->
                while (rows.next()) rows.getString("INDEX_NAME")?.let { indexes += it.lowercase() }
            }
        }
        source.close()

        assertTrue(indexes.any { it.contains("name") }, "expected an index on name, got: $indexes")
    }

    @Test
    fun `a renamed column is reported rather than silently added beside the old one`() = runTest {
        // The 4.5 -> 5.0 shape: 4.5 derived column names as snake_case, 5.0 uses the property name
        // verbatim. Additive migration then puts an empty "playerUuid" beside a populated
        // "player_uuid" and every row reads back as its Kotlin defaults, with nothing thrown and
        // nothing logged. Every plugin with real data in a 4.5 table hits this on first boot.
        val capture = Capture()
        val source = h2()
        source.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate("CREATE TABLE players (id VARCHAR(64) PRIMARY KEY, player_uuid VARCHAR(64))")
            }
        }

        SculkData.using(source, SqlDialect.MYSQL, capturingLogger(capture)).repository<PlayerRow, String>()
        source.close()

        val warning = capture.records.singleOrNull { it.contains("does not model") }
        assertTrue(warning != null, "expected a warning naming both sets, got: ${capture.records}")
        assertTrue(warning!!.contains("player_uuid"), warning)
        assertTrue(warning.contains("coins"), "it must also name what was added: $warning")
    }

    @Test
    fun `a table that simply gains a column is not reported as a rename`() = runTest {
        // The same additive path, minus the abandoned column. Warning on this too would train
        // everyone to ignore the one that matters.
        val capture = Capture()
        val source = h2()
        source.connection.use { connection ->
            connection.createStatement().use {
                it.executeUpdate("CREATE TABLE players (id VARCHAR(64) PRIMARY KEY, coins BIGINT)")
            }
        }

        SculkData.using(source, SqlDialect.MYSQL, capturingLogger(capture)).repository<PlayerRow, String>()
        source.close()

        assertTrue(capture.records.none { it.contains("does not model") }, "got: ${capture.records}")
    }

    @Test
    fun `a fresh table is not reported as a rename`() = runTest {
        val capture = Capture()
        val source = h2()

        SculkData.using(source, SqlDialect.MYSQL, capturingLogger(capture)).repository<PlayerRow, String>()
        source.close()

        assertTrue(capture.records.none { it.contains("does not model") }, "got: ${capture.records}")
    }

    @Test
    fun `a failed query is logged before it is returned`() = runTest {
        val capture = Capture()
        val source = h2()
        val repo = SculkData.using(source, SqlDialect.MYSQL, capturingLogger(capture)).repository<PlayerRow, String>()

        // topBy on a column the entity does not declare: the failure is a caller error, and the
        // point of the test is that it reaches the log rather than only the ignored return value.
        val result = repo.topBy("not_a_column", 5)
        source.close()

        assertTrue(result.isFailure)
        assertEquals(1, capture.records.size, "exactly one log line, got: ${capture.records}")
        assertTrue(capture.records.single().contains("players"), capture.records.single())
    }
}
