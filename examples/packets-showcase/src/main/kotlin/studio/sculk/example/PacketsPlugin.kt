package studio.sculk.example

import studio.sculk.core.onFailure
import studio.sculk.core.onSuccess
import studio.sculk.packets.PacketBackendMode
import studio.sculk.platform.SculkPlugin

public class PacketsPlugin :
    SculkPlugin({
        packets {
            backend = PacketBackendMode.Auto
            required = false
        }
    }) {
    override fun setup() {
        val packets = sculk.packetsResult
        packets?.onSuccess { service ->
            logger.info("Sculk packets enabled with ${service.backend}.")
        }
        packets?.onFailure { message, _ ->
            logger.warning(message)
        }
    }
}
