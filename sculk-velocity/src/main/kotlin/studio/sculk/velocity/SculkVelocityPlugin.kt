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
 */
@SculkStable
public abstract class SculkVelocityPlugin(
    protected val proxy: ProxyServer,
    protected val logger: Logger,
    protected val dataDirectory: Path,
    protected val container: PluginContainer,
) {
    private var runtime: VelocityRuntime? = null

    /** The runtime; available from [start] until shutdown. */
    public val sculk: VelocityRuntime get() = checkNotNull(runtime) { "${container.description.id} has no Sculk runtime yet." }

    protected abstract suspend fun start()

    /** Must not suspend: shutdown continues as soon as it returns. */
    protected open fun stop() {}

    @Subscribe(order = PostOrder.FIRST)
    private fun onProxyInitialize(@Suppress("UNUSED_PARAMETER") event: ProxyInitializeEvent) {
        val created = SculkVelocity.attach(proxy, container, logger)
        runtime = created
        created.scope.launch(start = CoroutineStart.UNDISPATCHED) { start() }
    }

    @Subscribe(order = PostOrder.LAST)
    private fun onProxyShutdown(@Suppress("UNUSED_PARAMETER") event: ProxyShutdownEvent) {
        val current = runtime ?: return
        try {
            stop()
        } finally {
            current.close()
            runtime = null
        }
    }
}
