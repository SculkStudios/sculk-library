package studio.sculk.velocity

import com.velocitypowered.api.event.proxy.ListenerBoundEvent
import com.velocitypowered.api.network.ListenerType
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
        readyWhenListening()
    }

    /**
     * Enters READY when the proxy starts accepting players, after every plugin's initialisation,
     * as Paper's first tick does for a server.
     */
    private fun readyWhenListening() {
        val plugin = container.instance.orElseThrow { IllegalStateException("${container.description.id} has no instance yet.") }
        proxy.eventManager.register(plugin, ListenerBoundEvent::class.java) { event ->
            if (event.listenerType == ListenerType.MINECRAFT && !lifecycle.hasReached(LifecyclePhase.READY)) {
                lifecycle.advance(LifecyclePhase.READY)
            }
        }
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

    /**
     * Creates the runtime. Call from `ProxyInitializeEvent`, so READY follows when the proxy starts
     * listening; close it in `ProxyShutdownEvent`.
     */
    public fun attach(
        proxy: ProxyServer,
        container: PluginContainer,
        logger: Logger,
        developmentMode: Boolean = System.getProperty(DEVELOPMENT_PROPERTY).toBoolean(),
    ): VelocityRuntime {
        val runtime = VelocityRuntime(proxy, container, VelocityExecutor(proxy, container), Slf4jLog(logger), developmentMode)
        runtime.begin()
        return runtime
    }
}

private class Slf4jLog(private val logger: Logger) : SculkLog {
    override fun info(message: String) = logger.info(message)

    override fun warn(message: String, cause: Throwable?) = logger.warn(message, cause)

    override fun error(message: String, cause: Throwable?) = logger.error(message, cause)
}
