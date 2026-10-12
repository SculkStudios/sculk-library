package studio.sculk.itest.boot

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.getOrNull
import studio.sculk.version.MinecraftVersion
import java.io.File

/**
 * Proves a jar compiled on JDK 25 for JVM 21 loads, links against `sculk-core` and runs on a real
 * server: it compares the server's versions with the ones the build expects, writes one line per
 * check to `results.properties`, then stops the server.
 */
class SculkBootPlugin : JavaPlugin() {
    override fun onEnable() {
        // The first global tick runs after startup completes, so the report reflects a booted server.
        server.globalRegionScheduler.run(this) { report() }
    }

    private fun report() {
        val raw = server.minecraftVersion
        val expectedMinecraft = System.getProperty("sculk.itest.minecraft")
        val expectedJava = System.getProperty("sculk.itest.java")
        val parsed = MinecraftVersion.parse(raw).getOrNull()?.toString()
        val java = Runtime.version().feature().toString()
        val results = sortedMapOf(
            "minecraft-version" to verdict(raw == expectedMinecraft) { "server reports $raw, expected $expectedMinecraft" },
            "parsed-version" to verdict(parsed == expectedMinecraft) { "MinecraftVersion parsed '$parsed' from '$raw'" },
            "java-version" to verdict(java == expectedJava) { "running on Java $java, expected $expectedJava" },
        )
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.shutdown()
    }

    private fun verdict(ok: Boolean, detail: () -> String): String = if (ok) "PASS" else "FAIL: ${detail()}"
}
