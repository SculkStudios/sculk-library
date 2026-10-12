package studio.sculk.paper

import io.papermc.paper.ServerBuildInfo
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.capability.Capability
import studio.sculk.capability.CapabilityKey
import studio.sculk.execution.SectionPosition
import studio.sculk.runtime.PlatformInfo
import studio.sculk.version.MinecraftVersion
import java.io.File

/** What the running server is, detected once from its build info and its API. */
internal data class DetectedPlatform(val info: PlatformInfo, val sectionShift: Int)

internal object PaperPlatformDetector {
    private const val CANVAS_BRAND = "canvasmc:canvas"
    private const val REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer"

    fun detect(serverRoot: File): DetectedPlatform {
        val build = ServerBuildInfo.buildInfo()
        val brand = build.brandId().asString()
        val kind = when {
            brand == CANVAS_BRAND -> PlatformInfo.Kind.CANVAS
            classExists(REGIONIZED_SERVER) -> PlatformInfo.Kind.FOLIA
            else -> PlatformInfo.Kind.PAPER
        }
        val buildNumber = build.buildNumber().let { if (it.isPresent) " build ${it.asInt}" else "" }
        val info =
            PlatformInfo(
                kind,
                "${build.brandName()} ${build.minecraftVersionName()}$buildNumber",
                MinecraftVersion.parse(build.minecraftVersionId()),
            )
        val shift = if (kind.isRegionized) gridExponent(serverRoot) else SectionPosition.DEFAULT_SHIFT
        return DetectedPlatform(info, shift)
    }

    /**
     * Reads `threaded-regions.grid-exponent` from paper-global.yml without a YAML parser: the key is
     * unique in that file. Missing or unreadable means Folia's default.
     */
    private fun gridExponent(serverRoot: File): Int {
        val config = File(serverRoot, "config/paper-global.yml").takeIf(File::isFile) ?: return SectionPosition.DEFAULT_SHIFT
        val value = config.readLines().firstNotNullOfOrNull { line -> GRID_EXPONENT.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull() }
        return value?.coerceIn(0, 31) ?: SectionPosition.DEFAULT_SHIFT
    }

    private val GRID_EXPONENT = Regex("""\s+grid-exponent:\s*(\d+)\s*""")

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

    private data class Probe(val key: CapabilityKey<Unit>, val className: String, val since: String)

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
            val capability = if (PaperPlatformDetector.classExists(
                    probe.className,
                )
            ) {
                Capability.Native(Unit)
            } else {
                Capability.Unavailable("added in ${probe.since}")
            }
            builder.resolve(probe.key, capability)
        }
    }
}
