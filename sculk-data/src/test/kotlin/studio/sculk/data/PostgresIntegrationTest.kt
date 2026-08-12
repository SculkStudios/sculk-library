package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import studio.sculk.annotation.SculkInternal
import java.util.logging.Logger

/**
 * The only thing that can prove the Postgres path.
 *
 * H2 implements neither `ON CONFLICT … DO UPDATE` in its PostgreSQL mode nor in its default mode,
 * so unlike SQLite and MySQL the generated Postgres SQL cannot be exercised by an embedded engine.
 * [SqlDialectTest] pins the statement text; this pins that a real server accepts it.
 *
 * Skipped unless `SCULK_POSTGRES_URL` is set, e.g.
 * `jdbc:postgresql://localhost:5432/sculk?user=postgres&password=postgres`.
 */
@OptIn(SculkInternal::class)
@EnabledIfEnvironmentVariable(named = "SCULK_POSTGRES_URL", matches = ".+")
class PostgresIntegrationTest {
    private fun open() = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = System.getenv("SCULK_POSTGRES_URL")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 2
        },
    )

    private val ada = PlayerRow(id = "ada", name = "Ada", coins = 100, rank = Rank.VIP, homes = listOf("base"))

    @Test
    fun `save find update delete against a real postgres`() = runTest {
        val source = open()
        try {
            source.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate("DROP TABLE IF EXISTS \"players\"") }
            }
            val repo = SculkData.using(source, SqlDialect.POSTGRES, Logger.getLogger("pg")).repository<PlayerRow, String>()

            repo.save(ada).getOrThrow()
            assertEquals(ada, repo.find("ada").getOrThrow())

            repo.save(ada.copy(coins = 250)).getOrThrow()
            assertEquals(250L, repo.find("ada").getOrThrow()!!.coins)
            assertEquals(1L, repo.count().getOrThrow(), "ON CONFLICT DO UPDATE must update, not insert")

            repo.delete("ada").getOrThrow()
            assertNull(repo.find("ada").getOrThrow())
        } finally {
            source.close()
        }
    }

    @Test
    fun `a reserved column name is quoted correctly for postgres`() = runTest {
        val source = open()
        try {
            source.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate("DROP TABLE IF EXISTS \"orders\"") }
            }
            val repo = SculkData.using(source, SqlDialect.POSTGRES, Logger.getLogger("pg")).repository<ReservedRow, String>()

            repo.save(ReservedRow(id = "x", order = 3, group = "staff", key = "abc")).getOrThrow()

            assertEquals(ReservedRow("x", 3, "staff", "abc"), repo.find("x").getOrThrow())
        } finally {
            source.close()
        }
    }

    @Test
    fun `a second boot migrates and upserts against a real postgres`() = runTest {
        val source = open()
        try {
            SqlEngineContract.migrateReopenAndUpsert(source, SqlDialect.POSTGRES, Logger.getLogger("pg"))
        } finally {
            source.close()
        }
    }
}
