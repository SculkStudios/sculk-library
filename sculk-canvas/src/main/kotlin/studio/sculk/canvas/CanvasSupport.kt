package studio.sculk.canvas

import io.canvasmc.canvas.region.RegionThreadingTickManager
import org.bukkit.Bukkit
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.capability.Capability
import studio.sculk.capability.CapabilityKey
import studio.sculk.paper.PaperPlatformExtension
import studio.sculk.runtime.PlatformInfo

/**
 * Canvas support for a Paper runtime. Pass it to `Sculk.attach(plugin, extensions = listOf(CanvasSupport))`
 * or list it in `SculkPaperPlugin.platformExtensions`. On Paper and Folia every Canvas capability
 * resolves as unavailable, so code that branches on them runs everywhere.
 */
@SculkStable
public object CanvasSupport : PaperPlatformExtension {
    @SculkStable
    public val REGION_DATA: CapabilityKey<CanvasRegionData> = CapabilityKey("canvas.region-data", "Canvas regionized data")

    @SculkStable
    public val OWNERSHIP: CapabilityKey<CanvasOwnership> =
        CapabilityKey("canvas.ownership", "Canvas bounding-box and movement ownership checks")

    @SculkStable
    public val WORLDS: CapabilityKey<CanvasWorlds> = CapabilityKey("canvas.worlds", "Canvas async world unloading")

    @SculkStable
    public val TICK_MANAGER: CapabilityKey<RegionThreadingTickManager> = CapabilityKey("canvas.tick-manager", "Canvas region tick manager")

    override val id: String = "sculk-canvas"

    override fun resolve(context: PaperPlatformExtension.Context, capabilities: Capabilities.Builder) {
        if (context.platform.kind != PlatformInfo.Kind.CANVAS) {
            val reason = "requires Canvas; this server is ${context.platform.kind.name.lowercase()}"
            capabilities.resolve(REGION_DATA, Capability.Unavailable(reason))
            capabilities.resolve(OWNERSHIP, Capability.Unavailable(reason))
            capabilities.resolve(WORLDS, Capability.Unavailable(reason))
            capabilities.resolve(TICK_MANAGER, Capability.Unavailable(reason))
            return
        }
        val server = Bukkit.getServer()
        capabilities.resolve(REGION_DATA, Capability.Native(CanvasRegionData(server, context.log, context.developmentMode)))
        capabilities.resolve(OWNERSHIP, Capability.Native(CanvasOwnership(server)))
        capabilities.resolve(WORLDS, Capability.Native(CanvasWorlds(server)))
        capabilities.resolve(TICK_MANAGER, Capability.Native(server.regionThreadingTickRateManager))
    }

    /** Canvas reports its section shift directly; the configuration file is only a fallback. */
    override fun sectionShift(context: PaperPlatformExtension.Context): Int? {
        if (context.platform.kind != PlatformInfo.Kind.CANVAS) return null
        return Bukkit.getWorlds().firstOrNull()?.regionizer?.sectionShift
    }
}
