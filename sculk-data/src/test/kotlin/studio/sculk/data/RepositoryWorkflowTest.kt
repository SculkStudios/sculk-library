package studio.sculk.data

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import studio.sculk.data.repository.PlayerProfileStore
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository

class RepositoryWorkflowTest {
    @Test
    fun `player profile store creates and saves missing profile`() = runBlocking {
        val repo = MemoryRepository<PlayerProfile, Int> { it.id }
        val store = PlayerProfileStore(repo) { id -> PlayerProfile(id, coins = 100) }

        val result = store.getOrCreate(1)

        assertTrue(result is SculkResult.Success)
        assertEquals(100, (result as SculkResult.Success).value.coins)
        assertEquals(100, (repo.find(1) as SculkResult.Success).value?.coins)
    }

    private data class PlayerProfile(val id: Int, val coins: Long)

    private class MemoryRepository<T : Any, ID : Any>(private val idOf: (T) -> ID) : SculkRepository<T, ID> {
        private val values = linkedMapOf<ID, T>()

        override suspend fun find(id: ID): SculkResult<T?> = SculkResult.success(values[id])

        override suspend fun findAll(): SculkResult<List<T>> = SculkResult.success(values.values.toList())

        override suspend fun save(entity: T): SculkResult<Unit> {
            values[idOf(entity)] = entity
            return SculkResult.success(Unit)
        }

        override suspend fun saveAll(entities: List<T>): SculkResult<Unit> {
            entities.forEach { values[idOf(it)] = it }
            return SculkResult.success(Unit)
        }

        override suspend fun delete(id: ID): SculkResult<Unit> {
            values.remove(id)
            return SculkResult.success(Unit)
        }

        override suspend fun exists(id: ID): SculkResult<Boolean> = SculkResult.success(id in values)

        override suspend fun query(block: QueryBuilder<T>.() -> Unit): SculkResult<List<T>> = SculkResult.success(values.values.toList())
    }
}
