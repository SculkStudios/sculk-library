package studio.sculk.itest.velocity

import com.google.inject.Inject
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import studio.sculk.command.CommandDispatcher
import studio.sculk.command.CommandResult
import studio.sculk.command.CommandSource
import studio.sculk.command.CommandSpec
import studio.sculk.command.SenderKind
import studio.sculk.command.command
import studio.sculk.command.velocity.commands
import studio.sculk.command.velocity.server
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Runs the proxy scenarios on a real Velocity, writes `results.properties` and shuts the proxy down. */
class VelocityThreadingPlugin @Inject constructor(
    proxy: ProxyServer,
    logger: Logger,
    @DataDirectory dataDirectory: Path,
    container: PluginContainer,
) : SculkVelocityPlugin(proxy, logger, dataDirectory, container) {
    private val results = ConcurrentHashMap<String, String>()
    private val recorded = ConcurrentLinkedQueue<String>()

    private val specs: List<CommandSpec> = listOf(
        command("vtest-pay") {
            val target = word("player")
            val amount = double("amount", min = 0.01)
            executes { recorded += "pay ${this[target]} ${this[amount]}" }
        },
        command("vtest-heal") {
            val amount = integer("amount", min = 1, max = 20).optional(20)
            executes { recorded += "heal ${this[amount]}" }
        },
        command("vtest-eco", "vtest-economy") {
            literal("give", "add") {
                val amount = long("amount", min = 1)
                executes { recorded += "give ${this[amount]}" }
            }
        },
        command("vtest-mute") {
            val length = duration("length", max = 30.hours)
            val reason = choice("reason", mapOf("spam" to "Spam", "abuse" to "Abuse")).optional("Spam")
            executes { recorded += "mute ${this[length]} ${this[reason]}" }
        },
        command("vtest-note") {
            val title = string("title")
            val body = greedy("body")
            executes { recorded += "note ${this[title]}|${this[body]}" }
        },
        command("vtest-context") {
            executes { recorded += "proxy ${sculk.scope.execution.executor.owns(ExecutionContext.Proxy)}" }
        },
    )

    private val inputs = listOf(
        "vtest-pay Alex 12.5",
        "vtest-pay Alex 0",
        "vtest-heal",
        "vtest-heal 21",
        "vtest-economy add 5",
        "vtest-eco take 5",
        "vtest-mute 1h30m",
        "vtest-mute 90s ABUSE",
        "vtest-mute 2h nonsense",
        "vtest-mute 31h",
        "vtest-note \"shopping list\" eggs and milk",
        "vtest-context",
    )

    override suspend fun start() {
        val commands = sculk.commands()
        commands.register(*specs.toTypedArray())
        commands.register(
            command("vtest-server") {
                val target = server("server")
                executes { recorded += "server ${this[target].serverInfo.name}" }
            },
        )
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
            scenario("proxy-and-interpreter-agree") { conformance() }
            scenario("unknown-server-is-refused-on-the-proxy") {
                val outcome = runOnProxy("vtest-server nowhere")
                check(outcome == null) { "recorded '$outcome'" }
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

    private suspend fun conformance(): String {
        val interpreter = CommandDispatcher().apply { specs.forEach(::register) }
        val mismatches = inputs.mapNotNull { input ->
            val proxied = runOnProxy(input)
            val outcome = interpreter.execute(ConsoleSource, input)
            val interpreted = recorded.poll().also { recorded.clear() }
            val expected = if (input == "vtest-context") "proxy true" else interpreted
            val agree = proxied == expected && (outcome == CommandResult.Success) == (interpreted != null)
            if (agree) null else "'$input': proxy '$proxied', interpreter '$interpreted' (${outcome::class.simpleName})"
        }
        return if (mismatches.isEmpty()) "PASS (${inputs.size} inputs)" else "FAIL: ${mismatches.joinToString("; ")}"
    }

    /** Runs [input] as the proxy console and returns what the executor recorded, or null. */
    private suspend fun runOnProxy(input: String): String? {
        recorded.clear()
        proxy.commandManager.executeAsync(proxy.consoleCommandSource, input).await()
        delayTicks(4)
        return recorded.poll().also { recorded.clear() }
    }

    /** The console as the interpreter sees it; on a proxy, commands start on the plugin executor. */
    private object ConsoleSource : CommandSource {
        override val name: String = "Console"
        override val kind: SenderKind = SenderKind.CONSOLE
        override val uniqueId: UUID? = null
        override val audience: Audience = object : Audience {
            override fun sendMessage(message: Component) = Unit
        }
        override val executionContext: ExecutionContext = ExecutionContext.Proxy
        override val platformSender: Any = this

        override fun hasPermission(permission: String): Boolean = true
    }

    @Synchronized
    private fun write() {
        Files.createDirectories(dataDirectory)
        val text = results.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
        Files.writeString(dataDirectory.resolve("results.properties"), text)
    }
}
