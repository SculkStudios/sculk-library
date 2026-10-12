package studio.sculk.paper

import org.bukkit.plugin.Plugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.diagnostics.DiagnosticReport
import studio.sculk.event.SculkEvents
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.runtime.AbstractSculkRuntime
import studio.sculk.runtime.PlatformInfo
import studio.sculk.runtime.SculkLog
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The runtime for one plugin on Paper, Folia or Canvas. Create it with [Sculk.attach] or extend
 * [SculkPaperPlugin]. It enters READY on the first server tick, after every plugin has enabled.
 */
@SculkStable
public class PaperRuntime internal constructor(
    public val plugin: Plugin,
    platform: PlatformInfo,
    private val executor: PaperExecutor,
    log: SculkLog,
    developmentMode: Boolean,
    private val extensions: List<PaperPlatformExtension>,
    private val extensionContext: PaperPlatformExtension.Context,
) : AbstractSculkRuntime(plugin.name, platform, executor, log, developmentMode) {
    /** Region sections are `2^regionShift` chunks wide; Folia's and Canvas's `grid-exponent`. */
    public val regionShift: Int get() = executor.regionShift

    /** Paper events through Sculk's listen and observe handlers, owned by the plugin scope. */
    public val events: SculkEvents by lazy { PaperEvents(this) }

    /** Permission descriptors registered with Bukkit, removed when the plugin scope closes. */
    public val permissions: PaperPermissions by lazy { PaperPermissions(this) }

    internal fun begin() {
        lifecycle.advance(LifecyclePhase.CONFIGURE)
        lifecycle.advance(LifecyclePhase.LOAD)
        lifecycle.advance(LifecyclePhase.START)
        val capabilities = Capabilities.Builder()
        PaperCapabilities.resolve(capabilities)
        extensions.forEach { it.resolve(extensionContext, capabilities) }
        resolveCapabilities(capabilities.build())
        val ready = executor.schedule(
            ExecutionContext.Global,
            1,
            object : SculkExecutor.Work {
                override fun run() {
                    if (!lifecycle.hasReached(LifecyclePhase.READY)) lifecycle.advance(LifecyclePhase.READY)
                }
            },
        )
        scope.resources.own(SculkHandle { ready.close() })
    }

    override fun DiagnosticReport.Builder.platformSections() {
        section("Paper") {
            entry("Region shift", "$regionShift (${1 shl regionShift} chunks)")
            entry("Live tasks", executor.liveTasks)
            entry("Extensions", extensions.joinToString { it.id }.ifEmpty { "none" })
        }
    }

    override fun afterClose() {
        executor.shutdown()
    }
}

/** Entry points for plugins that keep their own `JavaPlugin` subclass. */
@SculkStable
public object Sculk {
    /** System property that turns development checks on for every plugin: `-Dsculk.development=true`. */
    public const val DEVELOPMENT_PROPERTY: String = "sculk.development"

    /**
     * Creates the runtime for [plugin]. Call from `onEnable`, and close the returned runtime in
     * `onDisable`: Paper fires its disable event before `onDisable`, so the runtime cannot close
     * itself without pulling resources out from under the plugin's own shutdown code.
     */
    public fun attach(
        plugin: Plugin,
        developmentMode: Boolean = developmentModeByDefault(),
        extensions: List<PaperPlatformExtension> = emptyList(),
    ): PaperRuntime {
        val log = PaperLog(plugin.logger)
        val detected = PaperPlatformDetector.detect(log)
        val context = PaperPlatformExtension.Context(plugin, detected.info, log, developmentMode)
        val executor =
            PaperExecutor(plugin, detected.info.kind.name.lowercase().replaceFirstChar(Char::uppercaseChar), detected.regionShift)
        return PaperRuntime(plugin, detected.info, executor, log, developmentMode, extensions, context).also(PaperRuntime::begin)
    }

    public fun developmentModeByDefault(): Boolean = System.getProperty(DEVELOPMENT_PROPERTY).toBoolean()
}

private class PaperLog(private val logger: Logger) : SculkLog {
    override fun info(message: String) = logger.info(message)

    override fun warn(message: String, cause: Throwable?) = logger.log(Level.WARNING, message, cause)

    override fun error(message: String, cause: Throwable?) = logger.log(Level.SEVERE, message, cause)
}
