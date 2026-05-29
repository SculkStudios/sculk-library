package studio.sculk.example

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkResult
import studio.sculk.data.repository.PlayerProfileStore
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository
import java.util.UUID

class ProfileServiceTest {
    private val repository = MemoryRepository()
    private val service = ProfileService(PlayerProfileStore(repository, serviceFactory()), clock = { 10_000 })

    @Test
    fun `first join creates a default profile`() =
        runBlocking {
            val uuid = UUID.randomUUID()
            val result = service.loadForJoin(uuid, "Steve") as SculkResult.Success

            assertEquals("Steve", result.value.name)
            assertEquals(1, result.value.joins)
            assertEquals(100, result.value.coins)
        }

    @Test
    fun `existing profile increments joins`() =
        runBlocking {
            val uuid = UUID.randomUUID()
            service.loadForJoin(uuid, "Steve")
            val second = service.loadForJoin(uuid, "Steve") as SculkResult.Success

            assertEquals(2, second.value.joins)
        }

    @Test
    fun `quit updates last seen`() =
        runBlocking {
            val uuid = UUID.randomUUID()
            service.loadForJoin(uuid, "Steve")

            assertTrue(service.saveAndUnload(uuid) is SculkResult.Success)
            assertEquals(10_000, (repository.find(uuid) as SculkResult.Success).value?.lastSeen)
        }

    @Test
    fun `disable flush saves loaded profiles`() =
        runBlocking {
            val uuid = UUID.randomUUID()
            service.loadForJoin(uuid, "Steve")

            assertTrue(service.flushLoaded() is SculkResult.Success)
            assertEquals("Steve", (repository.find(uuid) as SculkResult.Success).value?.name)
        }

    private fun serviceFactory(): (UUID) -> PlayerProfile =
        { uuid ->
            PlayerProfile(uuid, "", 10_000, 10_000, 0, 0, 0, 100)
        }

    private class MemoryRepository : SculkRepository<PlayerProfile, UUID> {
        private val values = linkedMapOf<UUID, PlayerProfile>()

        override suspend fun find(id: UUID): SculkResult<PlayerProfile?> = SculkResult.success(values[id])

        override suspend fun findAll(): SculkResult<List<PlayerProfile>> = SculkResult.success(values.values.toList())

        override suspend fun save(entity: PlayerProfile): SculkResult<Unit> {
            values[entity.uuid] = entity.copy()
            return SculkResult.success(Unit)
        }

        override suspend fun delete(id: UUID): SculkResult<Unit> {
            values.remove(id)
            return SculkResult.success(Unit)
        }

        override suspend fun exists(id: UUID): SculkResult<Boolean> = SculkResult.success(id in values)

        override suspend fun saveAll(entities: List<PlayerProfile>): SculkResult<Unit> {
            entities.forEach { save(it) }
            return SculkResult.success(Unit)
        }

        override suspend fun query(block: QueryBuilder<PlayerProfile>.() -> Unit): SculkResult<List<PlayerProfile>> =
            SculkResult.success(values.values.toList())
    }
}
