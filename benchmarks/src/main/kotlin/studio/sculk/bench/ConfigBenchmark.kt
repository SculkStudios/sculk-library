package studio.sculk.bench

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.sculk.SculkResult
import studio.sculk.config.Comment
import studio.sculk.config.ConfigFile
import studio.sculk.config.Range
import studio.sculk.config.SculkConfig
import studio.sculk.getOrThrow
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ShopSettings(
    @Comment("Shown when a player opens the shop.")
    val title: String = "<primary>Shop",
    @Range(min = 1, max = 54)
    val rows: Int = 6,
    val currency: String = "coins",
    val storage: ShopStorage = ShopStorage.Sqlite(),
    val categories: Map<String, List<String>> = mapOf(
        "blocks" to listOf("stone", "dirt", "oak_log"),
        "tools" to listOf("iron_pickaxe", "iron_axe"),
    ),
)

@Serializable
sealed interface ShopStorage {
    @Serializable
    @SerialName("sqlite")
    data class Sqlite(val file: String = "shop.db") : ShopStorage

    @Serializable
    @SerialName("mysql")
    data class MySql(val host: String = "localhost", val port: Int = 3306) : ShopStorage
}

/** Reading and validating a typical plugin configuration from disk, as `/reload` in a plugin does. */
@State(Scope.Benchmark)
class ConfigBenchmark {
    private lateinit var directory: Path
    private lateinit var config: ConfigFile<ShopSettings>

    @Setup
    fun setUp() {
        directory = Files.createTempDirectory("sculk-bench")
        config = SculkConfig.load<ShopSettings>(directory.resolve("shop.yml")).getOrThrow()
    }

    @TearDown
    fun tearDown() {
        config.close()
        directory.toFile().deleteRecursively()
    }

    @Benchmark
    fun reload(): SculkResult<ShopSettings> = config.reload()
}
