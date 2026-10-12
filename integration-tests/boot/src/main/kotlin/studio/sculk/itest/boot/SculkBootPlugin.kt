package studio.sculk.itest.boot

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.version.MinecraftVersion
import java.io.File

/**
 * Proves a jar compiled on JDK 25 for JVM 21 loads, links against `sculk-core` and runs on a real
 * server, then stops the server. The verifying build task reads the result file.
 */
class SculkBootPlugin : JavaPlugin() {
    override fun onEnable() {
        // The first global tick runs after startup completes, so the report reflects a booted server.
        server.globalRegionScheduler.run(this) { report() }
    }

    private fun report() {
        val raw = server.minecraftVersion
        val parsed = MinecraftVersion.parse(raw)
        val folia = runCatching { Class.forName("io.papermc.paper.threadedregions.RegionizedServer") }.isSuccess
        dataFolder.mkdirs()
        File(dataFolder, "result.properties").writeText(
            """
            minecraft=$raw
            parsed=${parsed ?: ""}
            java=${Runtime.version().feature()}
            regionized=$folia
            """.trimIndent() + "\n",
        )
        server.shutdown()
    }
}
