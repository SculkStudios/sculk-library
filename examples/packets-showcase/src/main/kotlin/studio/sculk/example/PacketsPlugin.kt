package studio.sculk.example

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.onFailure
import studio.sculk.core.onSuccess
import studio.sculk.packets.PacketBackendMode
import studio.sculk.platform.SculkPlatform

public class PacketsPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk =
            SculkPlatform.create(this) {
                packets {
                    backend = PacketBackendMode.Auto
                    required = false
                }
            }

        val packets = sculk.packetsResult
        packets?.onSuccess { service ->
            logger.info("Sculk packets enabled with ${service.backend}.")
        }
        packets?.onFailure { message, _ ->
            logger.warning(message)
        }
    }

    override fun onDisable() {
        if (::sculk.isInitialized) {
            sculk.close()
        }
    }
}
