package studio.sculk.runtime

import studio.sculk.annotation.SculkInternal
import studio.sculk.capability.Capabilities
import studio.sculk.diagnostics.DiagnosticReport
import studio.sculk.execution.SculkExecutor
import studio.sculk.execution.SculkScope
import studio.sculk.lifecycle.Lifecycle
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.service.ServiceRegistry
import studio.sculk.time.SculkClock

/**
 * The parts of a runtime every platform shares. Platform modules extend it and supply the executor,
 * the capabilities and the platform details.
 */
@SculkInternal
public abstract class AbstractSculkRuntime(
    final override val name: String,
    final override val platform: PlatformInfo,
    executor: SculkExecutor,
    final override val log: SculkLog,
    final override val developmentMode: Boolean,
    final override val clock: SculkClock = SculkClock.SYSTEM,
) : SculkRuntime {
    final override val lifecycle: Lifecycle = Lifecycle()
    final override val services: ServiceRegistry = ServiceRegistry()
    final override val scope: SculkScope = SculkScope.root(name, executor) { failure -> log.error("A coroutine in $name failed.", failure) }

    private val installed = LinkedHashMap<String, SculkModule>()
    private var resolvedCapabilities: Capabilities = Capabilities.NONE

    final override val capabilities: Capabilities get() = resolvedCapabilities

    /** Fixes the capabilities; platforms call it once, before READY. */
    protected fun resolveCapabilities(capabilities: Capabilities) {
        check(!lifecycle.hasReached(LifecyclePhase.READY)) { "Capabilities are fixed once the runtime is ready." }
        resolvedCapabilities = capabilities
    }

    final override fun install(module: SculkModule) {
        check(installed.putIfAbsent(module.id, module) == null) { "Module '${module.id}' is already installed in $name." }
        module.install(this)
    }

    /** Sections platforms add to the shared report. */
    protected open fun DiagnosticReport.Builder.platformSections() {}

    /** Runs after the plugin scope has closed, for platform cleanup such as cancelling stray tasks. */
    protected open fun afterClose() {}

    final override fun diagnostics(): DiagnosticReport = DiagnosticReport.build {
        section("Runtime") {
            entry("Plugin", name)
            entry("Platform", "${platform.kind} (${platform.brand})")
            entry("Minecraft", platform.minecraft ?: "proxy")
            entry("Java", platform.javaVersion)
            entry("Phase", lifecycle.phase ?: "not started")
            entry("Development checks", if (developmentMode) "on" else "off")
        }
        section("Modules") { installed.keys.forEach { entry(it, "installed") } }
        section("Capabilities") { capabilities.all().forEach { (key, capability) -> entry(key.id, capability.level) } }
        platformSections()
    }

    /** Closes the plugin scope in reverse order, reporting rather than throwing on failure. */
    final override fun close() {
        if (lifecycle.hasReached(LifecyclePhase.STOPPING)) return
        lifecycle.advance(LifecyclePhase.STOPPING)
        try {
            scope.close()
        } catch (failure: Exception) {
            log.error("Closing $name's resources failed; the rest were still closed.", failure)
        } finally {
            try {
                afterClose()
            } finally {
                lifecycle.advance(LifecyclePhase.STOPPED)
            }
        }
    }
}
