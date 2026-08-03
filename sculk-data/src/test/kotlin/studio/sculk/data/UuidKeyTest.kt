package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import java.util.UUID

/**
 * A player-keyed table, which is the shape most plugins actually store.
 *
 * `UUID` is not a primitive kotlinx-serialization knows, so it needs [UuidSerializer] naming at the
 * property. The docs teach this shape, so it is pinned here rather than assumed.
 */
@Serializable
@Table("uuid_keyed")
data class UuidKeyedRow(@Id @Serializable(with = UuidSerializer::class) val uuid: UUID, val coins: Long = 0)

@OptIn(SculkInternal::class)
class UuidKeyTest {
    private fun h2() = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DATABASE_TO_LOWER=TRUE"
            driverClassName = "org.h2.Driver"
        },
    )

    @Test
    fun `an entity keyed by uuid round-trips through a real engine`() = runTest {
        val source = h2()
        val repo = SculkData.using(source, SqlDialect.MYSQL, java.util.logging.Logger.getLogger("t"))
            .repository<UuidKeyedRow, UUID>()
        val id = UUID.randomUUID()

        repo.save(UuidKeyedRow(id, 42)).getOrThrow()
        val found = repo.find(id).getOrThrow()
        source.close()

        assertEquals(UuidKeyedRow(id, 42), found)
    }

    @Test
    fun `a uuid key is stored as text so an incident can be investigated in any client`() = runTest {
        val source = h2()
        val repo = SculkData.using(source, SqlDialect.MYSQL, java.util.logging.Logger.getLogger("t"))
            .repository<UuidKeyedRow, UUID>()
        val id = UUID.randomUUID()
        repo.save(UuidKeyedRow(id, 1)).getOrThrow()

        val stored = source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT uuid FROM uuid_keyed").use {
                    it.next()
                    it.getString(1)
                }
            }
        }
        source.close()

        assertEquals(id.toString(), stored)
    }
}
