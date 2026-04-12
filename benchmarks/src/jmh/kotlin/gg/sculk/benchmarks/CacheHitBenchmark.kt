package gg.sculk.benchmarks

import gg.sculk.core.SculkResult
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.data.SculkData
import gg.sculk.data.driver.SqlDialect
import gg.sculk.data.orm.Column
import gg.sculk.data.orm.PrimaryKey
import gg.sculk.data.orm.Table
import gg.sculk.data.repository.SculkRepository
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Table("bench_player")
public data class BenchPlayer(
    @PrimaryKey @Column("id") val id: UUID,
    @Column("score") val score: Long,
)

/**
 * Benchmarks the Caffeine cache hit path — verifies zero database round-trips on warm reads.
 *
 * Target: no DB calls on cache hit; throughput limited only by Caffeine reads.
 */
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@OptIn(SculkInternal::class)
public open class CacheHitBenchmark {
    private lateinit var sculkData: SculkData
    private lateinit var cachedRepo: SculkRepository<BenchPlayer, UUID>
    private val warmId: UUID = UUID.randomUUID()

    @Setup(Level.Trial)
    public fun setup() {
        val ds =
            com.zaxxer.hikari.HikariDataSource().also {
                it.jdbcUrl = "jdbc:h2:mem:bench_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                it.driverClassName = "org.h2.Driver"
                it.maximumPoolSize = 1
            }
        sculkData = SculkData.withDataSource(ds, SqlDialect.MYSQL)

        val repo = sculkData.repository<BenchPlayer, UUID>()
        repo.save(BenchPlayer(warmId, score = 9999L))

        cachedRepo =
            sculkData.cached(repo, { it.id }) {
                ttl = Duration.ofHours(1)
                maxSize = 1000
            }

        // Warm the cache — one DB call, then all subsequent reads hit Caffeine
        cachedRepo.find(warmId)
    }

    @TearDown(Level.Trial)
    public fun tearDown(): Unit = sculkData.close()

    /** Should hit Caffeine; zero DB calls. */
    @Benchmark
    public fun cacheHit(): SculkResult<BenchPlayer?> = cachedRepo.find(warmId)

    /** Should miss cache and fall through to DB. */
    @Benchmark
    public fun cacheMiss(): SculkResult<BenchPlayer?> = cachedRepo.find(UUID.randomUUID())
}
