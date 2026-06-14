package studio.sculk.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.data.cache.WriteBehindStore
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository
import java.util.concurrent.atomic.AtomicInteger

class WriteBehindStoreTest {
    private data class Row(val id: String, val value: Int)

    private class FakeRepository : SculkRepository<Row, String> {
        val store: MutableMap<String, Row> = LinkedHashMap()
        val saveAllCalls: AtomicInteger = AtomicInteger(0)
        var failNext: Boolean = false

        override suspend fun find(id: String): SculkResult<Row?> = SculkResult.success(store[id])

        override suspend fun findAll(): SculkResult<List<Row>> = SculkResult.success(store.values.toList())

        override suspend fun save(entity: Row): SculkResult<Unit> {
            store[entity.id] = entity
            return SculkResult.success(Unit)
        }

        override suspend fun delete(id: String): SculkResult<Unit> {
            store.remove(id)
            return SculkResult.success(Unit)
        }

        override suspend fun exists(id: String): SculkResult<Boolean> = SculkResult.success(store.containsKey(id))

        override suspend fun saveAll(entities: List<Row>): SculkResult<Unit> {
            saveAllCalls.incrementAndGet()
            if (failNext) {
                failNext = false
                return SculkResult.failure("boom")
            }
            entities.forEach { store[it.id] = it }
            return SculkResult.success(Unit)
        }

        override suspend fun query(block: QueryBuilder<Row>.() -> Unit): SculkResult<List<Row>> = SculkResult.success(emptyList())
    }

    @Test
    fun `coalesces repeated marks for the same key into one write`() = runTest {
        val repo = FakeRepository()
        val store = WriteBehindStore(repo) { it.id }
        store.markDirty(Row("a", 1))
        store.markDirty(Row("a", 2))
        store.markDirty(Row("b", 1))
        assertEquals(2, store.pending)

        assertTrue(store.flush() is SculkResult.Success)
        assertEquals(1, repo.saveAllCalls.get())
        assertEquals(2, repo.store["a"]?.value)
        assertEquals(0, store.pending)
    }

    @Test
    fun `flush is a no-op when nothing is dirty`() = runTest {
        val repo = FakeRepository()
        val store = WriteBehindStore(repo) { it.id }

        assertTrue(store.flush() is SculkResult.Success)
        assertEquals(0, repo.saveAllCalls.get())
    }

    @Test
    fun `remove drops a pending write before it is persisted`() = runTest {
        val repo = FakeRepository()
        val store = WriteBehindStore(repo) { it.id }
        store.markDirty(Row("a", 1))
        store.remove("a")
        assertEquals(0, store.pending)

        store.flush()
        assertTrue(repo.store.isEmpty())
    }

    @Test
    fun `failed flush re-queues entities for retry`() = runTest {
        val repo = FakeRepository()
        val store = WriteBehindStore(repo) { it.id }
        repo.failNext = true
        store.markDirty(Row("a", 1))

        assertTrue(store.flush() is SculkResult.Failure)
        assertEquals(1, store.pending)

        assertTrue(store.flush() is SculkResult.Success)
        assertEquals(1, repo.store["a"]?.value)
    }
}
