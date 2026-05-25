package studio.sculk.packets

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.SculkResult
import studio.sculk.core.scheduler.SculkScheduler
import java.util.ServiceLoader

public object SculkPacketServices {
    @JvmStatic
    public fun create(
        plugin: JavaPlugin,
        scheduler: SculkScheduler,
        config: PacketServiceConfig = PacketServiceConfig(),
    ): SculkResult<SculkPacketService> {
        if (config.backend == PacketBackendMode.Disabled) {
            return SculkResult.failure("Packet subsystem is disabled.")
        }

        val providers = ServiceLoader.load(SculkPacketServiceProvider::class.java).toList()
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

    private fun providerOrder(backend: PacketBackend): Int =
        when (backend) {
            PacketBackend.PacketEvents -> 0
            PacketBackend.ProtocolLib -> 1
        }

    private fun missingBackendMessage(
        mode: PacketBackendMode,
        discovered: Set<PacketBackend>,
    ): String {
        val requested =
            when (mode) {
                PacketBackendMode.Auto -> "PacketEvents or ProtocolLib"
                PacketBackendMode.PacketEvents -> "PacketEvents"
                PacketBackendMode.ProtocolLib -> "ProtocolLib"
                PacketBackendMode.Disabled -> "a packet backend"
            }
        val adapterHint =
            if (discovered.isEmpty()) {
                " Add sculk-packets-packetevents or sculk-packets-protocollib to your plugin."
            } else {
                ""
            }
        return "No available packet backend found for $requested. Install the server plugin and packet adapter module.$adapterHint"
    }
}
