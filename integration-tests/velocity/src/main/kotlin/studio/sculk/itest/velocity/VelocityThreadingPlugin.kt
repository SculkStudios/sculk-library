package studio.sculk.itest.velocity

import com.google.inject.Inject
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.withTimeout
import org.slf4j.Logger
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.UnsupportedContextException
import studio.sculk.execution.WorldKey
import studio.sculk.execution.delayTicks
import studio.sculk.execution.withAsync
import studio.sculk.execution.withExecutionContext
import studio.sculk.execution.withRegion
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.runtime.PlatformInfo
import studio.sculk.velocity.SculkVelocityPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Runs the proxy scenarios on a real Velocity, writes `results.properties` and shuts the proxy down. */
class VelocityThreadingPlugin @Inject constructor(
    proxy: ProxyServer,
    logger: Logger,
    @DataDirectory dataDirectory: Path,
    container: PluginContainer,
) : SculkVelocityPlugin(proxy, logger, dataDirectory, container) {
    private val results = ConcurrentHashMap<String, String>()

    override suspend fun start() {
        sculk.scope.launch {
            scenario("platform-detected") {
                check(sculk.platform.kind == PlatformInfo.Kind.VELOCITY && sculk.platform.minecraft == null) { "detected ${sculk.platform}" }
            }
            scenario("proxy-work-runs-on-plugin-executor") {
                val owned = withExecutionContext(ExecutionContext.Proxy) { sculk.scope.execution.executor.owns(ExecutionContext.Proxy) }
                val threadName = withExecutionContext(ExecutionContext.Proxy) { Thread.currentThread().name }
                check(owned && "nioEventLoop" !in threadName && "epoll" !in threadName.lowercase()) { "owned $owned on $threadName" }
            }
            scenario("ticks-are-wall-clock") {
                val started = System.nanoTime()
                delayTicks(4)
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000
                check(elapsedMillis in 190..2_000) { "4 ticks took $elapsedMillis ms" }
            }
            scenario("async-work-runs") {
                val ran = withAsync { true }
                check(ran) { "async block did not return" }
            }
            scenario("regions-are-refused-where-written") {
                try {
                    withRegion(WorldKey("minecraft:overworld"), 0, 0) {}
                    "FAIL: a proxy accepted region work"
                } catch (_: UnsupportedContextException) {
                    "PASS"
                }
            }
            scenario("ready-once-the-proxy-listens") {
                var waited = 0
                while (!sculk.lifecycle.hasReached(LifecyclePhase.READY) && waited++ < 400) delayTicks(1)
                check(sculk.lifecycle.phase == LifecyclePhase.READY) { "phase ${sculk.lifecycle.phase} after ${waited * 50} ms" }
            }
            write()
            proxy.shutdown()
        }
    }

    override fun stop() {
        results["shutdown-reached"] = "PASS"
        write()
    }

    private suspend fun scenario(name: String, block: suspend () -> String) {
        results[name] = try {
            withTimeout(30.seconds) { block() }
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    private fun check(condition: Boolean, detail: () -> String): String = if (condition) "PASS" else "FAIL: ${detail()}"

    @Synchronized
    private fun write() {
        Files.createDirectories(dataDirectory)
        val text = results.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
        Files.writeString(dataDirectory.resolve("results.properties"), text)
    }
}
