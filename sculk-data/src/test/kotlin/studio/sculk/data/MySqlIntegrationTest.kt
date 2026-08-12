package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import studio.sculk.annotation.SculkInternal
import java.util.logging.Logger

/**
 * The only thing that can prove the MySQL path.
 *
 * H2's MySQL mode is not MySQL. It accepts `CREATE INDEX IF NOT EXISTS`, which **real MySQL
 * rejects** — that syntax is a MariaDB extension, also present in SQLite and Postgres, and absent
 * from every MySQL release including 8.4. Because one [SqlDialect.MYSQL] serves MySQL and MariaDB
 * alike (the MariaDB driver talks to both), nothing in the dialect could branch on it, and the
 * statement was emitted unconditionally. Table setup therefore failed on every genuine MySQL server
 * at the point it created an index, and no embedded engine in the test suite could see it.
 *
 * Skipped unless `SCULK_MYSQL_URL` is set, e.g.
 * `jdbc:mysql://localhost:3306/sculk?user=root&password=root`. Point it at **MySQL, not MariaDB** —
 * MariaDB would have passed before the fix.
 */
@OptIn(SculkInternal::class)
@EnabledIfEnvironmentVariable(named = "SCULK_MYSQL_URL", matches = ".+")
class MySqlIntegrationTest {
    private fun open() = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = System.getenv("SCULK_MYSQL_URL")
            driverClassName = "org.mariadb.jdbc.Driver"
            maximumPoolSize = 2
        },
    )

    private val ada = PlayerRow(id = "ada", name = "Ada", coins = 100, rank = Rank.VIP, homes = listOf("base"))

    @Test
    fun `a table with an indexed column can be created twice against a real mysql`() = runTest {
        val source = open()
        try {
            source.connection.use { connection ->
                connection.createStatement().use { it.executeUpdate("DROP TABLE IF EXISTS `players`") }
            }

            // The first call creates the table and its index; the second finds both already there.
            // Before the fix the first call threw on the index statement.
            SculkData.using(source, SqlDialect.MYSQL, Logger.getLogger("mysql")).repository<PlayerRow, String>()
            val repo = SculkData.using(source, SqlDialect.MYSQL, Logger.getLogger("mysql")).repository<PlayerRow, String>()

            val indexes = mutableListOf<String>()
            source.connection.use { connection ->
                connection.metaData.getIndexInfo(null, null, "players", false, true).use { rows ->
                    while (rows.next()) rows.getString("INDEX_NAME")?.lowercase()?.let { indexes += it }
                }
            }
            assertTrue(
                indexes.count { "idx_players_name" in it } == 1,
                "expected exactly one index on name, got: $indexes",
            )

            repo.save(ada).getOrThrow()
            assertEquals(ada, repo.find("ada").getOrThrow())
            repo.save(ada.copy(coins = 250)).getOrThrow()
            assertEquals(1L, repo.count().getOrThrow(), "ON DUPLICATE KEY UPDATE must update, not insert")
            repo.delete("ada").getOrThrow()
        } finally {
            source.close()
        }
    }
}
