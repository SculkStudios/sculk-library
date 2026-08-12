package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import studio.sculk.annotation.SculkInternal
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * The migration path, written once and run against every real engine.
 *
 * A first boot proves almost nothing. Everything that has gone wrong in this layer went wrong on the
 * *second* one — the boot that finds a table already there, an index already there, and a column the
 * entity has gained since. `CREATE INDEX IF NOT EXISTS` failed exactly there, on genuine MySQL only,
 * and no embedded engine in the suite could see it: H2's MySQL mode accepts the syntax and SQLite
 * has it for real. So the same scenario runs against each server rather than being asserted per
 * engine, and the engines differ only in the URL and the driver.
 *
 * Each step is a shape that has already cost a release: an added column that must appear rather than
 * throw, an index that must not be created twice, and an upsert issued by an entity that predates a
 * column — which `REPLACE INTO` used to delete along with the row it "replaced".
 */
@OptIn(SculkInternal::class)
internal object SqlEngineContract {
    private val ada = PlayerRow(id = "ada", name = "Ada", coins = 100, rank = Rank.VIP, homes = listOf("base"))

    fun connect(url: String, driver: String): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            maximumPoolSize = 2
        },
    )

    fun dropTable(source: DataSource, dialect: SqlDialect, table: String) {
        source.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("DROP TABLE IF EXISTS ${dialect.quote(table)}") }
        }
    }

    /** The index names on [table], lower-cased, because each engine reports its own casing. */
    fun indexesOn(source: DataSource, table: String): List<String> {
        val found = mutableListOf<String>()
        source.connection.use { connection ->
            connection.metaData.getIndexInfo(null, null, table, false, true).use { rows ->
                while (rows.next()) rows.getString("INDEX_NAME")?.let { found += it.lowercase() }
            }
        }
        return found
    }

    /**
     * Create the schema, populate it, then reopen against it twice and write through the old entity.
     *
     * Leaves the table empty so the run is repeatable against a server that keeps its data.
     */
    suspend fun migrateReopenAndUpsert(source: DataSource, dialect: SqlDialect, logger: Logger) {
        dropTable(source, dialect, "players")

        SculkData.using(source, dialect, logger).repository<PlayerRow, String>().save(ada).getOrThrow()

        // A second SculkData is a second boot: repositories are cached per instance, so this re-runs
        // the whole migration — create, inspect, alter, index — against a schema that already exists.
        val upgraded = SculkData.using(source, dialect, logger).repository<PlayerRowV2, String>()
        val migrated = upgraded.find("ada").getOrThrow()
        assertEquals(7, migrated!!.prestige, "the column added by the newer entity must appear, at its Kotlin default")
        assertEquals(ada.name, migrated.name, "and the row that was already there must survive")

        // The index is what broke: on the first boot there is nothing to collide with, so only a
        // later one can find idx_players_name already present and try to create it again.
        SculkData.using(source, dialect, logger).repository<PlayerRow, String>()
        val indexes = indexesOn(source, "players")
        assertEquals(
            1,
            indexes.count { "idx_players_name" in it },
            "expected exactly one index on name after three opens, got: $indexes",
        )

        upgraded.save(migrated.copy(prestige = 42)).getOrThrow()

        // The old entity does not model prestige, so its upsert cannot mention that column. It must
        // therefore leave it alone: `REPLACE INTO` deleted the row and took the column with it.
        val legacy = SculkData.using(source, dialect, logger).repository<PlayerRow, String>()
        legacy.save(ada.copy(coins = 250)).getOrThrow()

        val read = upgraded.find("ada").getOrThrow()!!
        assertEquals(42, read.prestige, "an upsert must leave a column it does not list alone")
        assertEquals(1L, legacy.count().getOrThrow(), "and it must update in place rather than insert a second row")
        assertEquals(250L, read.coins)
        assertEquals(ada.name, read.name)
        assertEquals(ada.rank, read.rank, "an enum survives the round trip by name")
        assertEquals(ada.homes, read.homes, "as does a @Json column")
        assertEquals(ada.note, read.note)

        legacy.delete("ada").getOrThrow()
    }
}
