package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * Queries have to agree with the rows they are querying.
 *
 * The DSL is written against Kotlin properties, but a table has columns and a codec writes values
 * through serializers. Two things therefore have to be translated on the way into SQL, and neither
 * used to be:
 *
 *  - **the column name**, which differs from the property name as soon as `@Column` renames it
 *  - **the value**, because a property stored through a serializer is not stored as itself
 *
 * Both failed the same way: valid SQL, no error, no rows. That is the worst failure mode a query
 * layer has, because nothing distinguishes it from "nothing matched".
 */
@OptIn(SculkInternal::class)
class QuerySchemaTest {
    @Serializable
    @Table("query_schema_rows")
    data class Row(
        @Id val id: String,
        @Column("player_name") @Index val playerName: String = "",
        @Column("seen_at") @Index @Serializable(with = InstantSerializer::class) val seenAt: Instant = Instant.EPOCH,
        @Serializable(with = UuidSerializer::class) val owner: UUID = UUID(0, 0),
        val rank: Rank = Rank.DEFAULT,
    )

    private var source: HikariDataSource? = null
    private lateinit var data: SculkData
    private lateinit var repo: SculkRepository<Row, String>

    private val epoch = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeEach
    fun setUp() {
        val file = Files.createTempFile("query-schema", ".db")
        source = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:$file"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
            },
        )
        data = SculkData.using(source!!, SqlDialect.SQLITE, Logger.getLogger("query-schema-test"))
        repo = data.repository<Row, String>()
    }

    @AfterEach
    fun tearDown() {
        source?.close()
    }

    @Test
    fun `a query on a renamed column finds the row`() = runTest {
        repo.save(Row(id = "a", playerName = "Alice")).value()

        // Before the fix this rendered `WHERE "playerName" = ?` against a table whose column is
        // `player_name`, which SQLite rejects outright.
        val found = repo.findFirst { Row::playerName eq "Alice" }.value()

        assertNotNull(found)
        assertEquals("a", found?.id)
    }

    @Test
    fun `a range query on an Instant column finds the rows inside the range`() = runTest {
        repo.save(Row(id = "old", seenAt = epoch.minusSeconds(86_400))).value()
        repo.save(Row(id = "new", seenAt = epoch)).value()

        // The silent one. InstantSerializer stores epoch millis; the parameter arrived as an
        // Instant, so the driver stringified it and compared text against a BIGINT. No error, no
        // rows -- indistinguishable from "nothing matched".
        val recent = repo.query { Row::seenAt atLeast epoch.minusSeconds(60) }.value()

        assertEquals(listOf("new"), recent.map { it.id })
    }

    @Test
    fun `an equality query on an Instant column finds the row`() = runTest {
        repo.save(Row(id = "a", seenAt = epoch)).value()

        val found = repo.findFirst { Row::seenAt eq epoch }.value()

        assertEquals("a", found?.id)
    }

    @Test
    fun `a query on a UUID column finds the row`() = runTest {
        val owner = UUID.randomUUID()
        repo.save(Row(id = "a", owner = owner)).value()

        val found = repo.findFirst { Row::owner eq owner }.value()

        assertEquals("a", found?.id)
    }

    @Test
    fun `an IN query coerces every value`() = runTest {
        repo.save(Row(id = "a", seenAt = epoch)).value()
        repo.save(Row(id = "b", seenAt = epoch.plusSeconds(60))).value()

        val found = repo.query { Row::seenAt isIn listOf(epoch, epoch.plusSeconds(60)) }.value()

        assertEquals(2, found.size)
    }

    @Test
    fun `a query on an enum column matches by name`() = runTest {
        repo.save(Row(id = "a", rank = Rank.MVP)).value()
        repo.save(Row(id = "b", rank = Rank.DEFAULT)).value()

        val found = repo.query { Row::rank eq Rank.MVP }.value()

        assertEquals(listOf("a"), found.map { it.id })
    }

    @Test
    fun `ordering by a renamed column sorts rather than failing`() = runTest {
        repo.save(Row(id = "b", playerName = "Bob")).value()
        repo.save(Row(id = "a", playerName = "Alice")).value()

        val ordered = repo.query { orderBy(Row::playerName) }.value()

        assertEquals(listOf("a", "b"), ordered.map { it.id })
    }

    @Test
    fun `deleteWhere on an Instant column removes only the matching rows`() = runTest {
        repo.save(Row(id = "old", seenAt = epoch.minusSeconds(86_400))).value()
        repo.save(Row(id = "new", seenAt = epoch)).value()

        // The retention-sweep shape. Deleting nothing looks identical to having nothing to delete,
        // so this one would have rotted quietly for as long as it took someone to check.
        val removed = repo.deleteWhere { Row::seenAt lessThan epoch.minusSeconds(60) }.value()

        assertEquals(1, removed)
        assertEquals(listOf("new"), repo.findAll().value().map { it.id })
    }

    @Test
    fun `counting on a renamed column counts the matching rows`() = runTest {
        repo.save(Row(id = "a", playerName = "Alice")).value()
        repo.save(Row(id = "b", playerName = "Bob")).value()

        assertEquals(1L, repo.count { Row::playerName eq "Alice" }.value())
    }

    @Test
    fun `forProperty resolves a renamed column and falls back to the column name`() {
        val schema = TableSchema.of(kotlinx.serialization.serializer<Row>().descriptor)

        assertEquals("player_name", schema.forProperty("playerName")?.name)
        // topBy takes a column name, so both spellings have to resolve or it breaks.
        assertEquals("player_name", schema.forProperty("player_name")?.name)
    }

    private fun <T> SculkResult<T>.value(): T = when (this) {
        is SculkResult.Success -> value
        is SculkResult.Failure -> throw AssertionError("expected success but got: $message", cause)
    }
}
