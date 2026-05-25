package studio.sculk.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkHandle
import studio.sculk.core.SculkResult
import studio.sculk.core.scheduler.SculkScheduler
import studio.sculk.data.repository.PlayerProfileStore
import studio.sculk.data.repository.SculkRepository
import studio.sculk.data.repository.async

class RepositoryWorkflowTest {
    @Test
    fun `player profile store creates and saves missing profile`() {
        val repo = MemoryRepository<PlayerProfile, Int> { it.id }
        val store = PlayerProfileStore(repo) { id -> PlayerProfile(id, coins = 100) }

        val result = store.getOrCreate(1)

        assertTrue(result is SculkResult.Success)
        assertEquals(100, (result as SculkResult.Success).value.coins)
        assertEquals(100, (repo.find(1) as SculkResult.Success).value?.coins)
    }

    @Test
    fun `async repository delegates through scheduler`() {
        val repo = MemoryRepository<PlayerProfile, Int> { it.id }
        val async = repo.async(ImmediateScheduler())

        async.save(PlayerProfile(1, 250)).join()
        val result = async.find(1).join()

        assertTrue(result is SculkResult.Success)
        assertEquals(250, (result as SculkResult.Success).value?.coins)
    }

    private data class PlayerProfile(
        val id: Int,
        val coins: Long,
    )

    private class MemoryRepository<T : Any, ID : Any>(
        private val idOf: (T) -> ID,
    ) : SculkRepository<T, ID> {
        private val values = linkedMapOf<ID, T>()

        override fun find(id: ID): SculkResult<T?> = SculkResult.success(values[id])

        override fun findAll(): SculkResult<List<T>> = SculkResult.success(values.values.toList())

        override fun save(entity: T): SculkResult<Unit> {
            values[idOf(entity)] = entity
            return SculkResult.success(Unit)
        }

        override fun saveAll(entities: List<T>): SculkResult<Unit> {
            entities.forEach { values[idOf(it)] = it }
            return SculkResult.success(Unit)
        }

        override fun delete(id: ID): SculkResult<Unit> {
            values.remove(id)
            return SculkResult.success(Unit)
        }

        override fun exists(id: ID): SculkResult<Boolean> = SculkResult.success(id in values)
    }

    private class ImmediateScheduler : SculkScheduler {
        override fun runSync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runSyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runAsyncDelayed(
            delayTicks: Long,
            task: Runnable,
        ): SculkHandle = runAsync(task)

        override fun runAsyncRepeating(
            delayTicks: Long,
            periodTicks: Long,
            task: Runnable,
        ): SculkHandle = runAsync(task)
    }
}
