package studio.sculk.velocity

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.CoroutineStart
import org.slf4j.Logger
import studio.sculk.annotation.SculkStable
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * A Velocity plugin managed by Sculk. Velocity constructs the main class through Guice, so the
 * subclass declares one `@Inject` constructor and passes its arguments up:
 *
 * ```kotlin
 * class MyPlugin @Inject constructor(proxy: ProxyServer, logger: Logger, @DataDirectory dataDirectory: Path, container: PluginContainer) :
 *     SculkVelocityPlugin(proxy, logger, dataDirectory, container) {
 *     override suspend fun start() { ... }
 * }
 * ```
 *
 * [start] runs during `ProxyInitializeEvent`, synchronously until its first suspension. [stop]
 * runs during `ProxyShutdownEvent`, before the runtime closes and while executors still accept work.
 *
 * If [start] fails, timeouts included, the failure is logged once, [stop] runs and the runtime
 * closes, removing everything the plugin registered through Sculk. Velocity cannot disable a
 * plugin, so it stays loaded but inactive until the proxy restarts, rather than running half-initialised.
 */
@SculkStable
public abstract class SculkVelocityPlugin(
    protected val proxy: ProxyServer,
    protected val logger: Logger,
    protected val dataDirectory: Path,
    protected val container: PluginContainer,
) {
    private val runtime = AtomicReference<VelocityRuntime?>()

    /** The runtime; available from [start] until shutdown, or until a failed start closes it. */
    public val sculk: VelocityRuntime get() = checkNotNull(runtime.get()) { "${container.description.id} has no Sculk runtime." }

    /** Registers the plugin's commands, listeners and tasks. */
    protected abstract suspend fun start()

    /** Must not suspend: shutdown continues as soon as it returns. */
    protected open fun stop() {}

    @Subscribe(order = PostOrder.FIRST)
    private fun onProxyInitialize(@Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent) {
        val created = SculkVelocity.attach(proxy, container, logger)
        runtime.set(created)
        created.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                start()
            } catch (cancelled: CancellationException) {
                // A closed scope means the proxy is already shutting down; anything else is a failed start.
                if (created.scope.isClosed) throw cancelled
                fail(created, cancelled)
            } catch (failure: Exception) {
                fail(created, failure)
            }
        }
    }

    private fun fail(created: VelocityRuntime, failure: Throwable) {
        created.log.error("${container.description.id} failed to start; its Sculk runtime is closed until the proxy restarts.", failure)
        shutDown(created)
    }

    @Subscribe(order = PostOrder.LAST)
    private fun onProxyShutdown(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        runtime.get()?.let { shutDown(it) }
    }

    // A failed start and proxy shutdown can race; whichever takes the runtime stops and closes it, once.
    private fun shutDown(current: VelocityRuntime) {
        if (!runtime.compareAndSet(current, null)) return
        try {
            stop()
        } finally {
            current.close()
        }
    }
}
