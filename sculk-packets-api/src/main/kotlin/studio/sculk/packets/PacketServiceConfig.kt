package studio.sculk.packets

/**
 * Packet subsystem configuration used by `SculkPlatform.create { packets { ... } }`.
 */
public class PacketServiceConfig {
    /**
     * Backend selection mode. Auto prefers PacketEvents, then ProtocolLib.
     */
    public var backend: PacketBackendMode = PacketBackendMode.Auto

    /**
     * When true, platform startup fails if the requested backend is not available.
     */
    public var required: Boolean = false
}
