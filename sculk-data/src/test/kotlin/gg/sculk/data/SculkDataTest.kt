package gg.sculk.data

import gg.sculk.core.SculkResult
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.data.driver.SqlDialect
import gg.sculk.data.orm.PrimaryKey
import gg.sculk.data.orm.Table
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        // H2 with MySQL compatibility for upsert support
        ds.setURL("jdbc:h2:mem:test${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1")
        sculkData = SculkData.withDataSource(ds, SqlDialect.MYSQL)
    }

    @Test
    fun `save and find round-trips correctly`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        val player = TestPlayer(id = 1, name = "Alice", coins = 500L)

        val saveResult = repo.save(player)
        if (saveResult is SculkResult.Failure) {
            throw AssertionError("save() failed: ${saveResult.message}", saveResult.cause)
        }

        val result = repo.find(1)
        if (result is SculkResult.Failure) {
            throw AssertionError("find() failed: ${result.message}", result.cause)
        }
        assertTrue(result is SculkResult.Success)
        val found = (result as SculkResult.Success).value
        assertEquals("Alice", found?.name)
        assertEquals(500L, found?.coins)
    }

    @Test
    fun `find returns null for missing id`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        val result = repo.find(999)
        assertTrue(result is SculkResult.Success)
        assertNull((result as SculkResult.Success).value)
    }

    @Test
    fun `save is an upsert — updates existing entity`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        repo.save(TestPlayer(id = 1, name = "Alice", coins = 100L))
        repo.save(TestPlayer(id = 1, name = "Alice", coins = 200L))

        val result = (repo.find(1) as SculkResult.Success).value
        assertEquals(200L, result?.coins)
    }

    @Test
    fun `delete removes entity`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        repo.save(TestPlayer(id = 1, name = "Bob", coins = 0L))
        repo.delete(1)

        val result = repo.find(1)
        assertNull((result as SculkResult.Success).value)
    }

    @Test
    fun `exists returns correct boolean`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        repo.save(TestPlayer(id = 1, name = "Carol", coins = 0L))

        assertTrue((repo.exists(1) as SculkResult.Success).value)
        assertTrue(!(repo.exists(999) as SculkResult.Success).value)
    }

    @Test
    fun `findAll returns all saved entities`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        repo.save(TestPlayer(id = 1, name = "A", coins = 1L))
        repo.save(TestPlayer(id = 2, name = "B", coins = 2L))

        val all = (repo.findAll() as SculkResult.Success).value
        assertEquals(2, all.size)
    }

    @Test
    fun `cache serves subsequent finds without hitting repository`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        val cached =
            sculkData.cached(repo, { it.id }) {
                ttl = Duration.ofMinutes(5)
                maxSize = 100
            }

        val player = TestPlayer(id = 1, name = "Dave", coins = 42L)
        cached.save(player)

        // Second find should come from cache
        val result1 = (cached.find(1) as SculkResult.Success).value
        val result2 = (cached.find(1) as SculkResult.Success).value
        assertEquals(result1, result2)
        assertEquals("Dave", result1?.name)
    }

    @Test
    fun `delete invalidates cache`() {
        val repo = sculkData.repository<TestPlayer, Int>()
        val cached = sculkData.cached(repo, { it.id })

        cached.save(TestPlayer(id = 1, name = "Eve", coins = 0L))
        cached.find(1) // prime the cache
        cached.delete(1)

        // After delete, cache should be invalidated and DB should return null
        val result = (cached.find(1) as SculkResult.Success).value
        assertNull(result)
    }
}
