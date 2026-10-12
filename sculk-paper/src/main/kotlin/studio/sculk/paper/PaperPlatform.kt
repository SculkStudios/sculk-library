package studio.sculk.paper

import io.papermc.paper.ServerBuildInfo
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.capability.Capability
import studio.sculk.capability.CapabilityKey
import studio.sculk.execution.SectionPosition
import studio.sculk.getOrNull
import studio.sculk.runtime.PlatformInfo
import studio.sculk.runtime.SculkLog
import studio.sculk.version.MinecraftVersion
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/** What the running server is, detected once from its build info and its API. */
internal class DetectedPlatform(val info: PlatformInfo, val regionShift: Int)

internal object PaperPlatformDetector {
    private const val CANVAS_BRAND = "canvasmc:canvas"
    private const val CANVAS_CLASS = "io.canvasmc.canvas.region.RegionTickData"
    private const val REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer"
    private const val TICK_REGIONS = "io.papermc.paper.threadedregions.TickRegions"

    fun detect(log: SculkLog): DetectedPlatform {
        val build = ServerBuildInfo.buildInfo()
        val kind = when {
            build.brandId().asString() == CANVAS_BRAND || classExists(CANVAS_CLASS) -> PlatformInfo.Kind.CANVAS
            classExists(REGIONIZED_SERVER) -> PlatformInfo.Kind.FOLIA
            else -> PlatformInfo.Kind.PAPER
        }
        val buildNumber = build.buildNumber().let { if (it.isPresent) " build ${it.asInt}" else "" }
        val versionId = build.minecraftVersionId()
        val minecraft = MinecraftVersion.parse(versionId).getOrNull()
        if (minecraft ==
            null
        ) {
            log.warn("Sculk cannot read the Minecraft version '$versionId'; version-dependent features assume the newest.")
        }
        val info = PlatformInfo(kind, "${build.brandName()} ${build.minecraftVersionName()}$buildNumber", minecraft)
        return DetectedPlatform(info, if (kind.isRegionized) liveRegionShift(log) else SectionPosition.DEFAULT_SHIFT)
    }

    /**
     * Folia and Canvas fix the section shift at startup and expose it only on an internal class, so
     * it is read once through a method handle. Guessing wrong would send region work to the wrong
     * region, so a failure is logged loudly rather than silently defaulted.
     */
    private fun liveRegionShift(log: SculkLog): Int = try {
        val type = Class.forName(TICK_REGIONS, false, PaperPlatformDetector::class.java.classLoader)
        val handle = MethodHandles.publicLookup().findStatic(
            type,
            "getRegionChunkShift",
            MethodType.methodType(Int::class.javaPrimitiveType),
        )
        (handle.invoke() as Int).also { require(it in 0..31) { "shift $it is out of range" } }
    } catch (failure: ReflectiveOperationException) {
        fallbackShift(log, failure)
    } catch (failure: IllegalArgumentException) {
        fallbackShift(log, failure)
    } catch (failure: LinkageError) {
        fallbackShift(log, failure)
    }

    private fun fallbackShift(log: SculkLog, failure: Throwable): Int {
        log.error(
            "Sculk cannot read this server's region size and assumes grid-exponent ${SectionPosition.DEFAULT_SHIFT}. " +
                "If threaded-regions.grid-exponent is set to anything else, region batches may run off their region.",
            failure,
        )
        return SectionPosition.DEFAULT_SHIFT
    }

    fun classExists(name: String): Boolean = try {
        Class.forName(name, false, PaperPlatformDetector::class.java.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: LinkageError) {
        false
    }
}

/**
 * Paper features whose presence depends on the Minecraft version, probed by class presence because
 * Paper backports features mid-line (COMPATIBILITY.md). Feature modules attach their services to
 * these keys; until then each resolves to [Unit].
 */
@SculkStable
public object PaperCapabilities {
    @SculkStable
    public val DATA_COMPONENTS: CapabilityKey<Unit> = CapabilityKey("paper.data-components", "Item data components")

    @SculkStable
    public val MENU_TYPES: CapabilityKey<Unit> = CapabilityKey("paper.menu-types", "Typed menu views")

    @SculkStable
    public val DIALOGS: CapabilityKey<Unit> = CapabilityKey("paper.dialogs", "Dialogs")

    @SculkStable
    public val CONFIGURATION_PHASE: CapabilityKey<Unit> = CapabilityKey("paper.configuration-phase", "Configuration-phase connections")

    @SculkStable
    public val DATAPACK_DISCOVERY: CapabilityKey<Unit> = CapabilityKey("paper.datapack-discovery", "Plugin datapack discovery")

    @SculkStable
    public val MANNEQUINS: CapabilityKey<Unit> = CapabilityKey("paper.mannequins", "Mannequin entities")

    @SculkStable
    public val REMOVAL_REASONS: CapabilityKey<Unit> = CapabilityKey("paper.removal-reasons", "Entity removal reasons")

    @SculkStable
    public val POST_EFFECTS: CapabilityKey<Unit> = CapabilityKey("paper.post-effects", "Player post-processing effects")

    private class Probe(val key: CapabilityKey<Unit>, val className: String, val since: String)

    private val PROBES = listOf(
        Probe(DATA_COMPONENTS, "io.papermc.paper.datacomponent.DataComponentTypes", "Minecraft 1.21.3"),
        Probe(MENU_TYPES, "org.bukkit.inventory.MenuType", "Minecraft 1.21.1"),
        Probe(DIALOGS, "io.papermc.paper.dialog.Dialog", "Minecraft 1.21.7"),
        Probe(CONFIGURATION_PHASE, "io.papermc.paper.connection.PlayerConfigurationConnection", "Minecraft 1.21.7"),
        Probe(DATAPACK_DISCOVERY, "io.papermc.paper.datapack.DatapackRegistrar", "Minecraft 1.21.4"),
        Probe(MANNEQUINS, "org.bukkit.entity.Mannequin", "Minecraft 1.21.11"),
        Probe(REMOVAL_REASONS, "io.papermc.paper.entity.RemovalReason", "Minecraft 26.2 (build 109)"),
        Probe(POST_EFFECTS, "io.papermc.paper.entity.PlayerPostEffects", "Minecraft 26.3"),
    )

    internal fun resolve(builder: Capabilities.Builder) {
        PROBES.forEach { probe ->
            val present = PaperPlatformDetector.classExists(probe.className)
            builder.resolve(probe.key, if (present) Capability.Native(Unit) else Capability.Unavailable("added in ${probe.since}"))
        }
    }
}
