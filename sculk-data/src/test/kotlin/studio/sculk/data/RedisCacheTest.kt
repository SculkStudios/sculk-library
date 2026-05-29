package studio.sculk.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.core.SculkResult
import studio.sculk.data.cache.RedisBackend
import studio.sculk.data.cache.RedisCache
import studio.sculk.data.repository.QueryBuilder
import studio.sculk.data.repository.SculkRepository
import java.time.Duration

@Serializable
private data class CachedAccount(
    val id: Int,
    val coins: Long,
)

class RedisCacheTest {
    @Test
    fun `save serializes into redis and find reads it back without hitting the delegate`() =
        runBlocking {
            val delegate = CountingRepository()
            val backend = FakeRedisBackend()
            val cache =
                RedisCache(
                    delegate = delegate,
                    idExtractor = CachedAccount::id,
                    serializer = CachedAccount.serializer(),
                    backend = backend,
                    keyPrefix = "acct",
                    ttl = Duration.ofMinutes(5),
                )

            cache.save(CachedAccount(1, 500))
            delegate.findCalls = 0

            val found = (cache.find(1) as SculkResult.Success).value
            assertEquals(500L, found?.coins)
            assertEquals(0, delegate.findCalls, "find should be served from Redis")
            assertTrue(backend.store.keys.any { it == "acct:1" })
        }

    @Test
    fun `delete removes the redis entry`() =
        runBlocking {
            val delegate = CountingRepository()
            val backend = FakeRedisBackend()
            val cache =
                RedisCache(delegate, CachedAccount::id, CachedAccount.serializer(), backend, "acct", Duration.ofMinutes(5))

            cache.save(CachedAccount(1, 10))
            cache.delete(1)

            assertNull(backend.store["acct:1"])
        }

    private class FakeRedisBackend : RedisBackend {
        val store: MutableMap<String, String> = linkedMapOf()

        override suspend fun get(key: String): String? = store[key]

        override suspend fun set(
            key: String,
            value: String,
            ttlSeconds: Long,
        ) {
            store[key] = value
        }

        override suspend fun delete(key: String) {
            store.remove(key)
        }

        override suspend fun deleteByPrefix(prefix: String) {
            store.keys.removeIf { it.startsWith("$prefix:") }
        }

        override fun close() = Unit
    }

    private class CountingRepository : SculkRepository<CachedAccount, Int> {
        private val values = linkedMapOf<Int, CachedAccount>()
        var findCalls = 0

        override suspend fun find(id: Int): SculkResult<CachedAccount?> {
            findCalls++
            return SculkResult.success(values[id])
        }

        override suspend fun findAll(): SculkResult<List<CachedAccount>> = SculkResult.success(values.values.toList())

        override suspend fun save(entity: CachedAccount): SculkResult<Unit> {
            values[entity.id] = entity
            return SculkResult.success(Unit)
        }

        override suspend fun delete(id: Int): SculkResult<Unit> {
            values.remove(id)
            return SculkResult.success(Unit)
        }

        override suspend fun exists(id: Int): SculkResult<Boolean> = SculkResult.success(id in values)

        override suspend fun saveAll(entities: List<CachedAccount>): SculkResult<Unit> {
            entities.forEach { values[it.id] = it }
            return SculkResult.success(Unit)
        }

        override suspend fun query(block: QueryBuilder<CachedAccount>.() -> Unit): SculkResult<List<CachedAccount>> =
            SculkResult.success(values.values.toList())
    }
}
