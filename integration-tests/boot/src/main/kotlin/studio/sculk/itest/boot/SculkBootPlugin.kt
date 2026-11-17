package studio.sculk.itest.boot

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import org.bukkit.Keyed
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
        File(dataFolder, "registry-keys.txt").writeText(registryKeys())
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.shutdown()
    }

    /**
     * Every entry key of the registries a configuration names entries of, one `registry key` line
     * each, sorted. The build compares these across anchors to keep the alias table complete.
     */
    private fun registryKeys(): String = REGISTRIES.flatMap { registry -> keys(registry).map { "${registry.key().asString()} ${it.asString()}" } }
        .sorted()
        .joinToString("\n", postfix = "\n")

    // Every registry here holds Keyed values; the list is typed by projection only.
    @Suppress("UNCHECKED_CAST")
    private fun keys(registry: RegistryKey<out Keyed>): List<Key> =
        RegistryAccess.registryAccess().getRegistry(registry as RegistryKey<Keyed>).map { it.key() }

    private companion object {
        val REGISTRIES: List<RegistryKey<out Keyed>> = listOf(
            RegistryKey.ITEM, RegistryKey.BLOCK, RegistryKey.ENTITY_TYPE, RegistryKey.SOUND_EVENT, RegistryKey.ENCHANTMENT,
            RegistryKey.MOB_EFFECT, RegistryKey.POTION, RegistryKey.BIOME, RegistryKey.PARTICLE_TYPE, RegistryKey.ATTRIBUTE,
            RegistryKey.GAME_EVENT, RegistryKey.VILLAGER_PROFESSION, RegistryKey.VILLAGER_TYPE, RegistryKey.DAMAGE_TYPE,
            RegistryKey.STRUCTURE, RegistryKey.STRUCTURE_TYPE, RegistryKey.TRIM_MATERIAL, RegistryKey.TRIM_PATTERN,
            RegistryKey.BANNER_PATTERN, RegistryKey.PAINTING_VARIANT, RegistryKey.INSTRUMENT, RegistryKey.JUKEBOX_SONG,
            RegistryKey.MENU, RegistryKey.FLUID, RegistryKey.CAT_VARIANT, RegistryKey.FROG_VARIANT, RegistryKey.WOLF_VARIANT,
            RegistryKey.MAP_DECORATION_TYPE,
        )
    }

    private fun verdict(ok: Boolean, detail: () -> String): String = if (ok) "PASS" else "FAIL: ${detail()}"
}
