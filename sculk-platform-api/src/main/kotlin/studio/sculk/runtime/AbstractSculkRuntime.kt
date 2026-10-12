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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The parts of a runtime every platform shares. Platform modules extend it and supply the executor,
 * the capabilities and the platform details.
 *
 * Thread-safe: on Folia the enabling thread, the global region and region threads all reach it.
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
    private val closing = AtomicBoolean()

    @Volatile
    private var resolvedCapabilities: Capabilities = Capabilities.NONE

    final override val capabilities: Capabilities get() = resolvedCapabilities

    /** Fixes the capabilities; platforms call it once, before READY. */
    protected fun resolveCapabilities(capabilities: Capabilities) {
        check(!lifecycle.hasReached(LifecyclePhase.READY)) { "Capabilities are fixed once the runtime is ready." }
        resolvedCapabilities = capabilities
    }

    /** Installs [module]; a module whose install throws is not recorded, so fixing it and retrying works. */
    final override fun install(module: SculkModule) {
        synchronized(installed) {
            check(installed.putIfAbsent(module.id, module) == null) { "Module '${module.id}' is already installed in $name." }
        }
        try {
            module.install(this)
        } catch (failure: Throwable) {
            synchronized(installed) { installed.remove(module.id) }
            throw failure
        }
    }

    /** Sections platforms add to the shared report. */
    protected open fun DiagnosticReport.Builder.platformSections() {}

    /** Runs after the plugin scope has closed, for platform cleanup such as cancelling stray tasks. */
    protected open fun afterClose() {}

    final override fun diagnostics(): DiagnosticReport {
        val modules = synchronized(installed) { installed.keys.toList() }
        return DiagnosticReport.build {
            section("Runtime") {
                entry("Plugin", name)
                entry("Platform", "${platform.kind} (${platform.brand})")
                entry("Minecraft", platform.minecraft ?: "proxy")
                entry("Java", platform.javaVersion)
                entry("Phase", lifecycle.phase ?: "not started")
                entry("Development checks", if (developmentMode) "on" else "off")
            }
            section("Modules") { modules.forEach { entry(it, "installed") } }
            section("Capabilities") { capabilities.all().forEach { (key, capability) -> entry(key.id, capability.level) } }
            platformSections()
        }
    }

    /**
     * Stops the runtime once: runs the STOPPING actions, closes the plugin scope, lets the platform
     * clean up and enters STOPPED. Every step runs even when an earlier one fails; failures are
     * logged rather than thrown, because a plugin's disable must not stop the server's shutdown.
     */
    final override fun close() {
        if (!closing.compareAndSet(false, true)) return
        step("Running $name's shutdown actions") { lifecycle.advance(LifecyclePhase.STOPPING) }
        step("Closing $name's resources") { scope.close() }
        step("Cleaning up $name's platform tasks") { afterClose() }
        step("Finishing $name's shutdown") { lifecycle.advance(LifecyclePhase.STOPPED) }
    }

    private inline fun step(what: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            log.error("$what failed; shutdown continued.", failure)
        }
    }
}
