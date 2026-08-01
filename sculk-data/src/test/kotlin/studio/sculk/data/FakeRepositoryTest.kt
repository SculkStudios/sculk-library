package studio.sculk.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult

/**
 * A fake that answers differently from the thing it stands in for is worse than no fake, so its
 * query behaviour is pinned against the same [Condition] tree the real repository renders to SQL.
 */
class FakeRepositoryTest {
    private data class Profile(val id: Int, val name: String, val coins: Int, val rank: String?)

    private fun repo() = FakeRepository<Profile, Int>(
        idOf = { it.id },
        columnsOf = { mapOf("id" to it.id, "name" to it.name, "coins" to it.coins, "rank" to it.rank) },
    )

    private fun <T> SculkResult<T>.value(): T = (this as SculkResult.Success).value

    @Test
    fun `a saved row is found by its id`() = runTest {
        val repo = repo()

        repo.save(Profile(1, "daisy", 100, "vip"))

        assertEquals("daisy", repo.find(1).value()?.name)
        assertTrue(repo.exists(1).value())
    }

    @Test
    fun `an absent id is null rather than a failure`() = runTest {
        assertNull(repo().find(99).value())
    }

    @Test
    fun `saving the same id twice replaces rather than duplicates`() = runTest {
        val repo = repo()

        repo.save(Profile(1, "daisy", 100, null))
        repo.save(Profile(1, "daisy", 250, null))

        assertEquals(1, repo.stored.size)
        assertEquals(250, repo.find(1).value()?.coins)
    }

    @Test
    fun `a delete removes the row and is counted as a write`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "daisy", 100, null))

        repo.delete(1)

        assertTrue(repo.stored.isEmpty())
        assertEquals(1, repo.writeCount, "given() seeds without counting; delete() writes")
    }

    @Test
    fun `a comparison query matches the same rows the sql would`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 50, null), Profile(2, "b", 150, null), Profile(3, "c", 250, null))

        val rich = repo.query { Profile::coins greaterThan 100 }.value()

        assertEquals(listOf(2, 3), rich.map { it.id }.sorted())
    }

    @Test
    fun `an any block is an OR rather than an AND`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 10, "vip"), Profile(2, "b", 10, "mvp"), Profile(3, "c", 10, null))

        val staff = repo.query {
            any {
                Profile::rank eq "vip"
                Profile::rank eq "mvp"
            }
        }.value()

        assertEquals(listOf(1, 2), staff.map { it.id }.sorted())
    }

    @Test
    fun `separate conditions are combined with AND`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 200, "vip"), Profile(2, "b", 200, null), Profile(3, "c", 10, "vip"))

        val result = repo.query {
            Profile::coins greaterThan 100
            Profile::rank eq "vip"
        }.value()

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun `isIn matches any listed value`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 0, null), Profile(2, "b", 0, null), Profile(3, "c", 0, null))

        assertEquals(listOf(1, 3), repo.query { Profile::id isIn listOf(1, 3) }.value().map { it.id }.sorted())
    }

    @Test
    fun `a null column compares equal to null and not to a value`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 0, null), Profile(2, "b", 0, "vip"))

        assertEquals(listOf(1), repo.query { Profile::rank eq null }.value().map { it.id })
    }

    @Test
    fun `take and skip page in order`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 10, null), Profile(2, "b", 20, null), Profile(3, "c", 30, null))

        val page = repo.query {
            orderBy(Profile::coins)
            skip(1)
            take(1)
        }.value()

        assertEquals(listOf(2), page.map { it.id })
    }

    @Test
    fun `topBy sorts descending by default`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 10, null), Profile(2, "b", 30, null), Profile(3, "c", 20, null))

        assertEquals(listOf(2, 3), repo.topBy("coins", 2).value().map { it.id })
    }

    @Test
    fun `count counts matches rather than rows`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 10, null), Profile(2, "b", 200, null))

        assertEquals(1L, repo.count { Profile::coins greaterThan 100 }.value())
        assertEquals(2L, repo.count().value())
    }

    @Test
    fun `deleteWhere reports how many went`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 10, null), Profile(2, "b", 200, null), Profile(3, "c", 300, null))

        assertEquals(2, repo.deleteWhere { Profile::coins greaterThan 100 }.value())
        assertEquals(listOf(1), repo.stored.map { it.id })
    }

    @Test
    fun `a set failure turns every call into that failure`() = runTest {
        val repo = repo()
        repo.given(Profile(1, "a", 10, null))
        repo.failure = "connection refused"

        // The branch that runs when the database is down, which a success-only fake never reaches.
        val result = repo.find(1)

        assertTrue(result is SculkResult.Failure)
        assertEquals("connection refused", (result as SculkResult.Failure).message)
    }

    @Test
    fun `a query without columnsOf fails loudly instead of matching everything`() {
        val bare = FakeRepository<Profile, Int>(idOf = { it.id })
        bare.given(Profile(1, "a", 10, null))

        // Silently returning every row would make the caller's test pass while its filter is wrong.
        val failure = assertThrows(IllegalStateException::class.java) {
            runTest { bare.query { Profile::coins greaterThan 5 } }
        }

        assertTrue(failure.message!!.contains("columnsOf"), failure.message)
    }

    @Test
    fun `id based reads need no columnsOf`() = runTest {
        val bare = FakeRepository<Profile, Int>(idOf = { it.id })

        bare.save(Profile(1, "daisy", 10, null))

        assertEquals("daisy", bare.find(1).value()?.name)
    }
}
