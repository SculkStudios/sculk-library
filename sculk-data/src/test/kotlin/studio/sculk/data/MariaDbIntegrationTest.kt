package studio.sculk.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import studio.sculk.annotation.SculkInternal
import java.util.logging.Logger

/**
 * The control for [MySqlIntegrationTest].
 *
 * One [SqlDialect.MYSQL] serves MySQL and MariaDB, so a fix aimed at one of them is a change to
 * both, and MariaDB is the half that was already working — it accepts `CREATE INDEX IF NOT EXISTS`,
 * which is why the broken statement passed every check anyone ran. Without this, the next repair to
 * that dialect could trade one engine for the other and the suite would report it as green.
 *
 * Skipped unless `SCULK_MARIADB_URL` is set, e.g.
 * `jdbc:mariadb://localhost:3306/sculk?user=root&password=root`.
 */
@OptIn(SculkInternal::class)
@EnabledIfEnvironmentVariable(named = "SCULK_MARIADB_URL", matches = ".+")
class MariaDbIntegrationTest {
    private fun open() = SqlEngineContract.connect(System.getenv("SCULK_MARIADB_URL"), "org.mariadb.jdbc.Driver")

    @Test
    fun `a second boot migrates and upserts against a real mariadb`() = runTest {
        val source = open()
        try {
            SqlEngineContract.migrateReopenAndUpsert(source, SqlDialect.MYSQL, Logger.getLogger("mariadb"))
        } finally {
            source.close()
        }
    }
}
