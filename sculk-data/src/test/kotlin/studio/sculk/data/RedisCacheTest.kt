package studio.sculk.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

@OptIn(SculkInternal::class)
class RedisCacheTest {
    /** Stands in for a server. [failing] makes every call throw, as an outage would. */
    private class FakeRedis : RedisBackend {
        val entries = mutableMapOf<String, String>()
        var failing = false
        var reads = 0

        private fun check() = check(!failing) { "redis is down" }

        override suspend fun get(key: String): String? {
            check()
            reads++
            return entries[key]
        }

        override suspend fun set(key: String, value: String, ttlSeconds: Long) {
            check()
            entries[key] = value
        }

        override suspend fun delete(key: String) {
            check()
            entries.remove(key)
        }

        override suspend fun deleteByPrefix(prefix: String) {
            check()
            entries.keys.removeIf { it.startsWith("$prefix:") }
        }

        override fun close() = Unit
    }

    private class Capture : Handler() {
        val records = mutableListOf<String>()

        override fun publish(record: LogRecord) {
            records += record.message
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    private fun repository(): SculkRepository<PlayerRow, String> {
        val source = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                driverClassName = "org.h2.Driver"
            },
        )
        return SculkData.using(source, SqlDialect.MYSQL, Logger.getLogger("t")).repository()
    }

    private fun cache(
        repository: SculkRepository<PlayerRow, String>,
        backend: FakeRedis,
        logger: Logger = Logger.getLogger("t"),
        clock: () -> Long = System::currentTimeMillis,
    ) = RedisCache(repository, PlayerRow::id, PlayerRow.serializer(), backend, "players", java.time.Duration.ofMinutes(10), logger, clock)

    private val ada = PlayerRow(id = "ada", name = "Ada", coins = 100, rank = Rank.VIP, homes = listOf("base"))

    @Test
    fun `a miss falls through to the database and is cached`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)
        repo.save(ada).getOrThrow()

        assertEquals(ada, cache.find("ada").getOrThrow())
        assertEquals(1, redis.entries.size, "the row must be written back to redis")
    }

    @Test
    fun `a hit is served without touching the database`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)
        cache.save(ada).getOrThrow()

        // Delete the row underneath: if the read still answers, it came from redis.
        repo.delete("ada").getOrThrow()

        assertEquals(ada, cache.find("ada").getOrThrow())
    }

    @Test
    fun `saving writes through to both`() = runTest {
        val repo = repository()
        val redis = FakeRedis()

        cache(repo, redis).save(ada).getOrThrow()

        assertEquals(ada, repo.find("ada").getOrThrow())
        assertTrue(redis.entries.containsKey("players:ada"))
    }

    @Test
    fun `deleting removes the row and the cached copy`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)
        cache.save(ada).getOrThrow()

        cache.delete("ada").getOrThrow()

        assertNull(repo.find("ada").getOrThrow())
        assertTrue(redis.entries.isEmpty())
    }

    @Test
    fun `an outage degrades to the database rather than failing the read`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)
        repo.save(ada).getOrThrow()

        redis.failing = true

        assertEquals(ada, cache.find("ada").getOrThrow(), "a cache outage must not take reads down with it")
        assertFalse(cache.available)
    }

    @Test
    fun `an outage logs once per window rather than once per lookup`() = runTest {
        val repo = repository()
        val redis = FakeRedis().also { it.failing = true }
        val capture = Capture()
        val logger = Logger.getLogger("redis-${UUID.randomUUID()}").apply {
            useParentHandlers = false
            addHandler(capture)
        }
        var now = 0L
        val cache = cache(repo, redis, logger) { now }

        repeat(50) { cache.find("ada") }
        assertEquals(1, capture.records.size, "50 lookups during an outage must not write 50 log lines")

        now += 31_000
        cache.find("ada")
        assertEquals(2, capture.records.size, "but the window does reopen")
    }

    @Test
    fun `an entry that no longer decodes is dropped rather than failing the lookup`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)
        repo.save(ada).getOrThrow()
        // A shape written by an older version of the plugin.
        redis.entries["players:ada"] = """{"totally":"different"}"""

        assertEquals(ada, cache.find("ada").getOrThrow())
        // Asserting the exact JSON would pin kotlinx-serialization formatting rather than the
        // behaviour; what matters is that the bad entry is gone and the replacement round-trips.
        val stored = redis.entries.getValue("players:ada")
        assertTrue(stored.contains("\"Ada\""), "the unreadable entry is replaced with a good one: $stored")
    }

    @Test
    fun `findOrCreate stores the created value in both`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)

        val created = cache.findOrCreate("new") { PlayerRow(id = it, name = "New") }.getOrThrow()

        assertEquals("New", created.name)
        assertEquals("New", repo.find("new").getOrThrow()!!.name)
        assertTrue(redis.entries.containsKey("players:new"))
    }

    @Test
    fun `clear drops only this prefix`() = runTest {
        val repo = repository()
        val redis = FakeRedis()
        val cache = cache(repo, redis)
        cache.save(ada).getOrThrow()
        redis.entries["other:thing"] = "keep"

        cache.clear()

        assertEquals(setOf("other:thing"), redis.entries.keys)
    }
}
