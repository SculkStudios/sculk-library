package studio.sculk.example

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkResult
import studio.sculk.data.cache.SculkCache
import studio.sculk.data.repository.SculkRepository
import java.time.Duration
import java.util.UUID

class EconomyServiceTest {
    private val repository = MemoryRepository()
    private val service =
        EconomyService(
            SculkCache(
                repository,
                EconomyAccount::uuid,
                Duration.ofMinutes(5),
                100,
            ),
            { 100 },
            { 1_000 },
        )

    @Test
    fun `deposit adds coins`() {
        val uuid = UUID.randomUUID()
        val result = service.deposit(uuid, "Steve", 50)

        assertTrue(result is SculkResult.Success)
        assertEquals(150, (result as SculkResult.Success).value.coins)
    }

    @Test
    fun `withdraw rejects insufficient funds`() {
        val result = service.withdraw(UUID.randomUUID(), "Alex", 200)

        assertTrue(result is SculkResult.Failure)
    }

    @Test
    fun `transfer moves coins atomically at service level`() {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()

        val result = service.transfer(from, "From", to, "To", 40)

        assertTrue(result is SculkResult.Success)
        val pair = (result as SculkResult.Success).value
        assertEquals(60, pair.first.coins)
        assertEquals(140, pair.second.coins)
    }

    @Test
    fun `pay self is rejected`() {
        val uuid = UUID.randomUUID()

        assertTrue(service.transfer(uuid, "Steve", uuid, "Steve", 1) is SculkResult.Failure)
    }

    @Test
    fun `negative or zero amount is rejected`() {
        assertTrue(service.deposit(UUID.randomUUID(), "Steve", 0) is SculkResult.Failure)
        assertTrue(service.withdraw(UUID.randomUUID(), "Steve", -1) is SculkResult.Failure)
    }

    @Test
    fun `top sorting returns highest balances first`() {
        service.set(UUID.randomUUID(), "A", 10)
        service.set(UUID.randomUUID(), "B", 50)
        service.set(UUID.randomUUID(), "C", 30)

        val top = service.top(3) as SculkResult.Success

        assertEquals(listOf("B", "C", "A"), top.value.map { it.name })
    }

    private class MemoryRepository : SculkRepository<EconomyAccount, UUID> {
        private val values = linkedMapOf<UUID, EconomyAccount>()

        override fun find(id: UUID): SculkResult<EconomyAccount?> = SculkResult.success(values[id])

        override fun findAll(): SculkResult<List<EconomyAccount>> = SculkResult.success(values.values.toList())

        override fun save(entity: EconomyAccount): SculkResult<Unit> {
            values[entity.uuid] = entity.copy()
            return SculkResult.success(Unit)
        }

        override fun delete(id: UUID): SculkResult<Unit> {
            values.remove(id)
            return SculkResult.success(Unit)
        }

        override fun exists(id: UUID): SculkResult<Boolean> = SculkResult.success(id in values)

        override fun saveAll(entities: List<EconomyAccount>): SculkResult<Unit> {
            entities.forEach { save(it) }
            return SculkResult.success(Unit)
        }
    }
}
