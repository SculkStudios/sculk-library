package studio.sculk.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult

/**
 * Verifies the [JavaRepository] bridge actually executes suspend operations and completes its
 * [java.util.concurrent.CompletableFuture]s with the correct [SculkResult]s — not just that it
 * compiles from Java.
 */
class JavaRepositoryTest {
    data class Person(val id: Int, val name: String)

    /** Minimal in-memory repository, no database needed. */
    private class FakeRepo : SculkRepository<Person, Int> {
        val store: MutableMap<Int, Person> = mutableMapOf()

        override suspend fun find(id: Int): SculkResult<Person?> = SculkResult.success(store[id])

        override suspend fun findAll(): SculkResult<List<Person>> = SculkResult.success(store.values.toList())

        override suspend fun save(entity: Person): SculkResult<Unit> {
            store[entity.id] = entity
            return SculkResult.success(Unit)
        }

        override suspend fun delete(id: Int): SculkResult<Unit> {
            store.remove(id)
            return SculkResult.success(Unit)
        }

        override suspend fun exists(id: Int): SculkResult<Boolean> = SculkResult.success(store.containsKey(id))

        override suspend fun saveAll(entities: List<Person>): SculkResult<Unit> {
            entities.forEach { store[it.id] = it }
            return SculkResult.success(Unit)
        }

        override suspend fun query(block: QueryBuilder<Person>.() -> Unit): SculkResult<List<Person>> =
            SculkResult.success(store.values.toList())
    }

    @Test
    fun `save then find round-trips through the future bridge`() {
        val repo = JavaRepository(FakeRepo())

        repo.save(Person(1, "Ada")).join()
        val found = repo.find(1).join()

        assertEquals("Ada", found.getOrNull()?.name)
        assertTrue(repo.exists(1).join().getOrThrow())
    }

    @Test
    fun `delete removes the entity`() {
        val repo = JavaRepository(FakeRepo())
        repo.save(Person(2, "Linus")).join()

        repo.delete(2).join()

        assertNull(repo.find(2).join().getOrNull())
    }

    @Test
    fun `findAll returns saved entities`() {
        val repo = JavaRepository(FakeRepo())
        repo.saveAll(listOf(Person(1, "Ada"), Person(2, "Linus"))).join()

        val all = repo.findAll().join().getOrThrow()

        assertEquals(2, all.size)
    }
}
