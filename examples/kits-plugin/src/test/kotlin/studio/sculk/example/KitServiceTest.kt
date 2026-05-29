package studio.sculk.example

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkResult
import studio.sculk.data.cache.CaffeineCache
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository
import java.time.Duration
import java.util.UUID

class KitServiceTest {
    private val repository = MemoryRepository()
    private var now = 1_000L
    private val service =
        KitService(
            settings = { KitSettings() },
            cooldowns = CaffeineCache(repository, KitCooldown::id, Duration.ofMinutes(5), 100),
            clock = { now },
        )

    @Test
    fun `first claim is allowed`() =
        runBlocking {
            val status = service.claimStatus(UUID.randomUUID(), "starter") as SculkResult.Success
            assertTrue(status.value.allowed)
        }

    @Test
    fun `claim is blocked during cooldown`() =
        runBlocking {
            val uuid = UUID.randomUUID()
            service.recordClaim(uuid, "starter")
            now += 1_000

            val status = service.claimStatus(uuid, "starter") as SculkResult.Success
            assertTrue(!status.value.allowed)
        }

    @Test
    fun `claim is allowed after cooldown expires`() =
        runBlocking {
            val uuid = UUID.randomUUID()
            service.recordClaim(uuid, "starter")
            now += 86_401_000

            val status = service.claimStatus(uuid, "starter") as SculkResult.Success
            assertTrue(status.value.allowed)
        }

    @Test
    fun `remaining time formats safely`() {
        assertEquals("1h 1m", service.formatRemaining(3_660_000))
        assertEquals("30s", service.formatRemaining(30_000))
    }

    private class MemoryRepository : SculkRepository<KitCooldown, String> {
        private val values = linkedMapOf<String, KitCooldown>()

        override suspend fun find(id: String): SculkResult<KitCooldown?> = SculkResult.success(values[id])

        override suspend fun findAll(): SculkResult<List<KitCooldown>> = SculkResult.success(values.values.toList())

        override suspend fun save(entity: KitCooldown): SculkResult<Unit> {
            values[entity.id] = entity.copy()
            return SculkResult.success(Unit)
        }

        override suspend fun delete(id: String): SculkResult<Unit> {
            values.remove(id)
            return SculkResult.success(Unit)
        }

        override suspend fun exists(id: String): SculkResult<Boolean> = SculkResult.success(id in values)

        override suspend fun saveAll(entities: List<KitCooldown>): SculkResult<Unit> {
            entities.forEach { save(it) }
            return SculkResult.success(Unit)
        }

        override suspend fun query(block: QueryBuilder<KitCooldown>.() -> Unit): SculkResult<List<KitCooldown>> =
            SculkResult.success(values.values.toList())
    }
}
