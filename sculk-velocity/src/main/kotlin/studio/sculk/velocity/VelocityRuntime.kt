package studio.sculk.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.diagnostics.DiagnosticReport
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.runtime.AbstractSculkRuntime
import studio.sculk.runtime.PlatformInfo
import studio.sculk.runtime.SculkLog

/**
 * The runtime for one Velocity plugin. Create it with [SculkVelocity.attach] in
 * `ProxyInitializeEvent`, or extend [SculkVelocityPlugin]; close it in `ProxyShutdownEvent`, while
 * Velocity's executors are still running.
 */
@SculkStable
public class VelocityRuntime internal constructor(
    public val proxy: ProxyServer,
    public val container: PluginContainer,
    private val executor: VelocityExecutor,
    log: SculkLog,
    developmentMode: Boolean,
) : AbstractSculkRuntime(
    container.description.id,
    PlatformInfo(PlatformInfo.Kind.VELOCITY, "${proxy.version.name} ${proxy.version.version}", null),
    executor,
    log,
    developmentMode,
) {
    internal fun begin() {
        lifecycle.advance(LifecyclePhase.CONFIGURE)
        lifecycle.advance(LifecyclePhase.LOAD)
        lifecycle.advance(LifecyclePhase.START)
        resolveCapabilities(Capabilities.NONE)
    }

    /** Called once the plugin has registered what it needs; Velocity has no first tick to wait for. */
    internal fun ready() {
        if (!lifecycle.hasReached(LifecyclePhase.READY)) lifecycle.advance(LifecyclePhase.READY)
    }

    override fun DiagnosticReport.Builder.platformSections() {
        section("Velocity") {
            entry("Live tasks", executor.liveTasks)
            entry("Players", proxy.playerCount)
            entry("Servers", proxy.allServers.size)
        }
    }

    override fun afterClose() {
        executor.shutdown()
    }
}

/** Entry points for Velocity plugins that keep their own main class. */
@SculkStable
public object SculkVelocity {
    /** System property that turns development checks on: `-Dsculk.development=true`. */
    public const val DEVELOPMENT_PROPERTY: String = "sculk.development"

    /** Creates the runtime. Call from `ProxyInitializeEvent`; close it in `ProxyShutdownEvent`. */
    public fun attach(
        proxy: ProxyServer,
        container: PluginContainer,
        logger: Logger,
        developmentMode: Boolean = System.getProperty(DEVELOPMENT_PROPERTY).toBoolean(),
    ): VelocityRuntime {
        val runtime = VelocityRuntime(proxy, container, VelocityExecutor(proxy, container), Slf4jLog(logger), developmentMode)
        runtime.begin()
        runtime.ready()
        return runtime
    }
}

private class Slf4jLog(private val logger: Logger) : SculkLog {
    override fun info(message: String) = logger.info(message)

    override fun warn(message: String, cause: Throwable?) = logger.warn(message, cause)

    override fun error(message: String, cause: Throwable?) = logger.error(message, cause)
}
