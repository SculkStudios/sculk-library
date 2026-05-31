package studio.sculk.data

import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.data.driver.SqlDialect
import studio.sculk.data.orm.PrimaryKey
import studio.sculk.data.orm.Table
import java.time.Duration

@Table("players")
private data class TestPlayer(
    @PrimaryKey val id: Int,
    val name: String,
    val coins: Long,
)

@OptIn(SculkInternal::class)
class SculkDataTest {
    private lateinit var sculkData: SculkData

    @BeforeEach
    fun setUp() {
        val ds = JdbcDataSource()
        ds.setURL("jdbc:h2:mem:test${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
        sculkData = SculkData.withDataSource(ds, SqlDialect.MYSQL)
    }

    @Test
    fun `save and find round-trips correctly`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            repo.save(TestPlayer(id = 1, name = "Alice", coins = 500L))

            val found = (repo.find(1) as SculkResult.Success).value
            assertEquals("Alice", found?.name)
            assertEquals(500L, found?.coins)
        }

    @Test
    fun `find returns null for missing id`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            assertNull((repo.find(999) as SculkResult.Success).value)
        }

    @Test
    fun `save is an upsert — updates existing entity`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            repo.save(TestPlayer(id = 1, name = "Alice", coins = 100L))
            repo.save(TestPlayer(id = 1, name = "Alice", coins = 200L))

            assertEquals(200L, (repo.find(1) as SculkResult.Success).value?.coins)
        }

    @Test
    fun `delete removes entity`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            repo.save(TestPlayer(id = 1, name = "Bob", coins = 0L))
            repo.delete(1)
            assertNull((repo.find(1) as SculkResult.Success).value)
        }

    @Test
    fun `exists returns correct boolean`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            repo.save(TestPlayer(id = 1, name = "Carol", coins = 0L))
            assertTrue((repo.exists(1) as SculkResult.Success).value)
            assertTrue(!(repo.exists(999) as SculkResult.Success).value)
        }

    @Test
    fun `query filters orders and limits`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            repo.saveAll(
                listOf(
                    TestPlayer(1, "A", 100L),
                    TestPlayer(2, "B", 5000L),
                    TestPlayer(3, "C", 2000L),
                ),
            )

            val rich =
                (
                    repo.query {
                        TestPlayer::coins greaterThan 1000L
                        orderByDescending(TestPlayer::coins)
                        limit(5)
                    } as SculkResult.Success
                ).value

            assertEquals(listOf(2, 3), rich.map { it.id })
        }

    @Test
    fun `transaction commits on success and rolls back on failure`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            repo.save(TestPlayer(1, "Tx", 10L))

            val failed =
                sculkData.transaction { conn ->
                    conn.prepareStatement("UPDATE players SET coins = 999 WHERE id = 1").use { it.executeUpdate() }
                    error("boom")
                }
            assertTrue(failed is SculkResult.Failure)
            assertEquals(10L, (repo.find(1) as SculkResult.Success).value?.coins)
        }

    @Test
    fun `cache serves subsequent finds and delete invalidates`() =
        runBlocking {
            val repo = sculkData.repository<TestPlayer, Int>()
            val cached = sculkData.cached(repo, { it.id }) { ttl = Duration.ofMinutes(5) }

            cached.save(TestPlayer(id = 1, name = "Dave", coins = 42L))
            assertEquals("Dave", (cached.find(1) as SculkResult.Success).value?.name)

            cached.delete(1)
            assertNull((cached.find(1) as SculkResult.Success).value)
        }
}
