package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import studio.sculk.annotation.SculkInternal
import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * The round trip, against real engines.
 *
 * SQLite is the real driver and H2's MySQL mode really does implement `ON DUPLICATE KEY UPDATE`,
 * so both upsert paths are exercised by something that parses SQL rather than by a string
 * comparison. H2 does **not** implement `ON CONFLICT … DO UPDATE` in either its PostgreSQL or its
 * default mode, so the Postgres path is covered by [SqlDialectTest] plus the env-gated
 * [PostgresIntegrationTest] instead of being faked here.
 */
@OptIn(SculkInternal::class)
class RepositoryRoundTripTest {
    enum class Backend(val dialect: SqlDialect) {
        SQLITE(SqlDialect.SQLITE),
        MYSQL_VIA_H2(SqlDialect.MYSQL),
    }

    private var source: HikariDataSource? = null

    @AfterEach
    fun tearDown() {
        source?.close()
    }

    private fun open(backend: Backend): DataSource {
        val unique = UUID.randomUUID().toString().replace("-", "")
        val config = HikariConfig().apply {
            when (backend) {
                Backend.SQLITE -> {
                    val file = Files.createTempFile("sculk", ".db").also { Files.delete(it) }
                    jdbcUrl = "jdbc:sqlite:$file"
                    driverClassName = "org.sqlite.JDBC"
                    maximumPoolSize = 1
                }

                Backend.MYSQL_VIA_H2 -> {
                    jdbcUrl = "jdbc:h2:mem:$unique;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                }
            }
        }
        return HikariDataSource(config).also { source = it }
    }

    private fun repo(backend: Backend): SculkRepository<PlayerRow, String> =
        SculkData.using(open(backend), backend.dialect, Logger.getLogger("test")).repository()

    private val ada = PlayerRow(
        id = "ada",
        name = "Ada",
        coins = 100,
        level = 3,
        ratio = 1.5,
        banned = false,
        rank = Rank.VIP,
        note = null,
        homes = listOf("base", "mine"),
    )

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `save find update delete`(backend: Backend) = runTest {
        val repo = repo(backend)

        repo.save(ada).getOrThrow()
        assertEquals(ada, repo.find("ada").getOrThrow())

        repo.save(ada.copy(coins = 250)).getOrThrow()
        assertEquals(250L, repo.find("ada").getOrThrow()!!.coins)
        assertEquals(1L, repo.count().getOrThrow(), "an update must not insert a second row")

        repo.delete("ada").getOrThrow()
        assertNull(repo.find("ada").getOrThrow())
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `saving an existing row preserves a column the entity does not declare`(backend: Backend) = runTest {
        // The REPLACE INTO regression. REPLACE deletes the row and inserts a new one, so anything
        // outside the statement is silently lost.
        val data = SculkData.using(open(backend), backend.dialect, Logger.getLogger("test"))
        val repo = data.repository<PlayerRow, String>()
        repo.save(ada).getOrThrow()

        data.transaction { connection ->
            val quote = backend.dialect::quote
            connection.createStatement().use {
                it.executeUpdate("ALTER TABLE ${quote("players")} ADD COLUMN ${quote("legacy")} VARCHAR(32)")
                it.executeUpdate("UPDATE ${quote("players")} SET ${quote("legacy")} = 'keep-me'")
            }
        }.getOrThrow()

        repo.save(ada.copy(coins = 999)).getOrThrow()

        val legacy = data.transaction { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT ${backend.dialect.quote("legacy")} FROM ${backend.dialect.quote("players")}")
                    .use { if (it.next()) it.getString(1) else null }
            }
        }.getOrThrow()

        assertEquals("keep-me", legacy, "the upsert must update in place, not replace the row")
        assertEquals(999L, repo.find("ada").getOrThrow()!!.coins)
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `enums round-trip by name and json columns survive`(backend: Backend) = runTest {
        val repo = repo(backend)

        repo.save(ada.copy(rank = Rank.MVP, homes = listOf("a", "b", "c"))).getOrThrow()

        val read = repo.find("ada").getOrThrow()!!
        assertEquals(Rank.MVP, read.rank)
        assertEquals(listOf("a", "b", "c"), read.homes)
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `a null column reads back as null`(backend: Backend) = runTest {
        val repo = repo(backend)

        repo.save(ada.copy(note = null)).getOrThrow()
        assertNull(repo.find("ada").getOrThrow()!!.note)

        repo.save(ada.copy(note = "seen")).getOrThrow()
        assertEquals("seen", repo.find("ada").getOrThrow()!!.note)
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `a column named with a reserved word works`(backend: Backend) = runTest {
        // order, group and key are reserved on at least one engine each; without quoting the
        // generated statement is a syntax error.
        val repo: SculkRepository<ReservedRow, String> =
            SculkData.using(open(backend), backend.dialect, Logger.getLogger("test")).repository()

        repo.save(ReservedRow(id = "x", order = 3, group = "staff", key = "abc")).getOrThrow()

        assertEquals(ReservedRow("x", 3, "staff", "abc"), repo.find("x").getOrThrow())
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `queries filter order and page in the database`(backend: Backend) = runTest {
        val repo = repo(backend)
        repo.saveAll(
            (1..10).map { ada.copy(id = "p$it", name = "P$it", coins = it * 10L, rank = if (it > 5) Rank.VIP else Rank.DEFAULT) },
        ).getOrThrow()

        val rich = repo.query {
            PlayerRow::coins greaterThan 50
            orderByDescending(PlayerRow::coins)
        }.getOrThrow()
        assertEquals(listOf(100L, 90L, 80L, 70L, 60L), rich.map { it.coins })

        val paged = repo.query {
            orderBy(PlayerRow::coins)
            take(3)
            skip(2)
        }.getOrThrow()
        assertEquals(listOf(30L, 40L, 50L), paged.map { it.coins })

        val either = repo.query {
            any {
                PlayerRow::name eq "P1"
                PlayerRow::name eq "P2"
            }
            orderBy(PlayerRow::name)
        }.getOrThrow()
        assertEquals(listOf("P1", "P2"), either.map { it.name })

        val within = repo.query { PlayerRow::name isIn listOf("P3", "P7") }.getOrThrow()
        assertEquals(2, within.size)
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `an empty IN matches nothing rather than failing to parse`(backend: Backend) = runTest {
        val repo = repo(backend)
        repo.save(ada).getOrThrow()

        assertEquals(emptyList<PlayerRow>(), repo.query { PlayerRow::name isIn emptyList() }.getOrThrow())
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `topBy sorts and limits in the database`(backend: Backend) = runTest {
        val repo = repo(backend)
        repo.saveAll((1..50).map { ada.copy(id = "p$it", coins = it.toLong()) }).getOrThrow()

        val top = repo.topBy("coins", rows = 3).getOrThrow()

        assertEquals(listOf(50L, 49L, 48L), top.map { it.coins })
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `count and deleteWhere operate without loading rows`(backend: Backend) = runTest {
        val repo = repo(backend)
        repo.saveAll((1..10).map { ada.copy(id = "p$it", coins = it.toLong()) }).getOrThrow()

        assertEquals(10L, repo.count().getOrThrow())
        assertEquals(4L, repo.count { PlayerRow::coins atMost 4 }.getOrThrow())

        assertEquals(4, repo.deleteWhere { PlayerRow::coins atMost 4 }.getOrThrow())
        assertEquals(6L, repo.count().getOrThrow())
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `findFirst applies a limit rather than filtering in memory`(backend: Backend) = runTest {
        val repo = repo(backend)
        repo.saveAll((1..5).map { ada.copy(id = "p$it", coins = it.toLong()) }).getOrThrow()

        val found = repo.findFirst {
            PlayerRow::coins atLeast 3
            orderBy(PlayerRow::coins)
        }.getOrThrow()

        assertEquals(3L, found!!.coins)
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `a field added in a later version migrates without losing rows`(backend: Backend) = runTest {
        val data = SculkData.using(open(backend), backend.dialect, Logger.getLogger("test"))
        data.repository<PlayerRow, String>().save(ada).getOrThrow()

        // Same table, one extra property with a default.
        val upgraded = data.repository<PlayerRowV2, String>()
        val read = upgraded.find("ada").getOrThrow()!!

        assertEquals("Ada", read.name, "the existing row survives")
        assertEquals(7, read.prestige, "and the new column takes its Kotlin default")
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `migrating twice is a no-op`(backend: Backend) = runTest {
        val source = open(backend)
        val logger = Logger.getLogger("test")
        SculkData.using(source, backend.dialect, logger).repository<PlayerRow, String>().save(ada).getOrThrow()

        val again = SculkData.using(source, backend.dialect, logger).repository<PlayerRow, String>()

        assertEquals(ada, again.find("ada").getOrThrow())
        assertEquals(1L, again.count().getOrThrow())
    }

    @ParameterizedTest
    @EnumSource(Backend::class)
    fun `a transaction rolls back on failure`(backend: Backend) = runTest {
        val data = SculkData.using(open(backend), backend.dialect, Logger.getLogger("test"))
        val repo = data.repository<PlayerRow, String>()
        repo.save(ada).getOrThrow()

        val result = data.transaction { connection ->
            connection.createStatement().use {
                it.executeUpdate("DELETE FROM ${backend.dialect.quote("players")}")
            }
            error("something went wrong halfway")
        }

        assertTrue(result.isFailure)
        assertEquals(1L, repo.count().getOrThrow(), "the delete must have been rolled back")
    }
}
