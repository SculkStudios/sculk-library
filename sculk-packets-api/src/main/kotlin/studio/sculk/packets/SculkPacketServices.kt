package studio.sculk.packets

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler
import java.util.ServiceLoader

@SculkStable
public object SculkPacketServices {
    public fun create(
        plugin: JavaPlugin,
        scheduler: SculkScheduler,
        config: PacketServiceConfig = PacketServiceConfig(),
    ): SculkResult<SculkPacketService> = create(plugin, scheduler, config, discoverProviders(plugin))

    /**
     * Packet backends ship inside the consuming plugin's jar, so discovery has to search that
     * plugin's class loader.
     *
     * The single-argument [ServiceLoader.load] searches the *thread context* class loader, which
     * during plugin enable belongs to the server — it cannot see the plugin's own jar, so every
     * backend looks uninstalled. The plugin's loader is tried first, then Sculk's own; the two
     * differ when Sculk is installed as a shared library rather than shaded.
     */
    private fun discoverProviders(plugin: JavaPlugin): List<SculkPacketServiceProvider> {
        val loaders =
            listOfNotNull(
                plugin.javaClass.classLoader,
                SculkPacketServiceProvider::class.java.classLoader,
            ).distinct()

        return loaders
            .flatMap { loader ->
                runCatching { ServiceLoader.load(SculkPacketServiceProvider::class.java, loader).toList() }
                    .getOrDefault(emptyList())
            }.distinctBy { it.javaClass.name }
    }

    internal fun create(
        plugin: JavaPlugin,
        scheduler: SculkScheduler,
        config: PacketServiceConfig,
        providers: List<SculkPacketServiceProvider>,
    ): SculkResult<SculkPacketService> {
        if (config.backend == PacketBackendMode.Disabled) {
            return SculkResult.failure("Packet subsystem is disabled.")
        }

        val ordered = providers.sortedBy { providerOrder(it.backend) }
        val candidates =
            when (config.backend) {
                PacketBackendMode.Auto -> ordered
                PacketBackendMode.PacketEvents -> ordered.filter { it.backend == PacketBackend.PacketEvents }
                PacketBackendMode.ProtocolLib -> ordered.filter { it.backend == PacketBackend.ProtocolLib }
                PacketBackendMode.Disabled -> emptyList()
            }

        val provider =
            candidates.firstOrNull { it.isAvailable() }
                ?: return SculkResult.failure(missingBackendMessage(config.backend, providers.map { it.backend }.toSet()))

        return SculkResult.success(provider.create(plugin, scheduler))
    }

    private fun providerOrder(backend: PacketBackend): Int = when (backend) {
        PacketBackend.PacketEvents -> 0
        PacketBackend.ProtocolLib -> 1
    }

    private fun missingBackendMessage(mode: PacketBackendMode, discovered: Set<PacketBackend>): String {
        val requested =
            when (mode) {
                PacketBackendMode.Auto -> "PacketEvents or ProtocolLib"
                PacketBackendMode.PacketEvents -> "PacketEvents"
                PacketBackendMode.ProtocolLib -> "ProtocolLib"
                PacketBackendMode.Disabled -> "a packet backend"
            }
        // Which of the two halves is missing changes the fix entirely, so say which.
        return if (discovered.isEmpty()) {
            "No packet adapter found for $requested. Add sculk-packets-packetevents or " +
                "sculk-packets-protocollib to your plugin's dependencies. If you shade Sculk, make sure " +
                "your shadow configuration merges META-INF/services descriptors."
        } else {
            "Adapters for ${discovered.joinToString(", ")} are present but none reported the server " +
                "plugin as available for $requested. Install PacketEvents or ProtocolLib on the server, " +
                "and make sure it loads before your plugin."
        }
    }
}
