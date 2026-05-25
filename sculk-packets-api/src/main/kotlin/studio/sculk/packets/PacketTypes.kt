package studio.sculk.packets

/**
 * Packet backend selected for a running Sculk packet service.
 */
public enum class PacketBackend {
    PacketEvents,
    ProtocolLib,
}

/**
 * Backend selection policy for platform startup.
 */
public enum class PacketBackendMode {
    Auto,
    PacketEvents,
    ProtocolLib,
    Disabled,
}

/**
 * Packet flow direction from the server's point of view.
 */
public enum class PacketDirection {
    Clientbound,
    Serverbound,
}

/**
 * Backend-neutral packet listener priority.
 */
public enum class PacketPriority {
    Lowest,
    Low,
    Normal,
    High,
    Highest,
    Monitor,
}

/**
 * Backend-neutral packet key.
 *
 * Sculk uses lowercase namespaced keys for public packet APIs so docs and configs do not need
 * backend-specific enum names.
 */
public data class PacketKey(
    public val namespace: String = "minecraft",
    public val value: String,
) {
    init {
        require(namespace.isNotBlank()) { "Packet namespace cannot be blank." }
        require(value.isNotBlank()) { "Packet value cannot be blank." }
    }

    public override fun toString(): String = "$namespace:$value"

    public companion object {
        @JvmStatic
        public fun of(input: String): PacketKey {
            val cleaned =
                input
                    .trim()
                    .lowercase()
                    .replace(' ', '_')
                    .replace('-', '_')
            val parts = cleaned.split(':', limit = 2)
            return if (parts.size == 2) {
                PacketKey(parts[0], parts[1])
            } else {
                PacketKey(value = cleaned)
            }
        }
    }
}

/**
 * Marker for backend-specific packets that can be sent through [SculkPacketService].
 */
public interface SculkPacket {
    public val direction: PacketDirection
    public val type: PacketKey
}
