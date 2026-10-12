package studio.sculk.itest.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import studio.sculk.command.Arguments
import studio.sculk.command.CommandDispatcher
import studio.sculk.command.CommandResult
import studio.sculk.command.CommandSource
import studio.sculk.command.CommandSpec
import studio.sculk.command.SenderKind
import studio.sculk.command.command
import studio.sculk.command.velocity.commands
import studio.sculk.command.velocity.server
import studio.sculk.event.EventPriority
import studio.sculk.event.await
import studio.sculk.event.listen
import studio.sculk.event.observe
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
    internal val recorded = ConcurrentLinkedQueue<String>()
    internal val proxyServer: ProxyServer get() = proxy
    private val permissionScenarios = ProxyPermissionScenarios(this)

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
        command("vtest-give") {
            val player = word("player")
            val silent = flag("silent", short = 's')
            val amount = flag("amount", Arguments.integer(1, 64), short = 'n')
            executes { recorded += "give ${this[player]} silent=${this[silent]} amount=${this[amount]}" }
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
        "vtest-give Steve -s -n 7",
        "vtest-give Steve --amount 99",
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
        commands.register(*permissionScenarios.specs.toTypedArray())
        permissionScenarios.register()
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
            scenario("await-handlers-decide-after-suspending") {
                val laterSaw = java.util.concurrent.atomic.AtomicBoolean(false)
                val observed = java.util.concurrent.atomic.AtomicBoolean(false)
                val handles = listOf(
                    sculk.events.await<LoginCheck> { event ->
                        delayTicks(2)
                        if (event.name == "griefer") event.result = ResultedEvent.GenericResult.denied()
                    },
                    sculk.events.listen<LoginCheck>(EventPriority.HIGH) { laterSaw.set(true) },
                    sculk.events.observe<LoginCheck>(includeCancelled = true) { observed.set(true) },
                )
                val denied = proxy.eventManager.fire(LoginCheck("griefer")).await()
                delayTicks(4)
                handles.forEach { it.close() }
                check(!denied.result.isAllowed && !laterSaw.get() && observed.get()) {
                    "allowed ${denied.result.isAllowed}, later handler ran ${laterSaw.get()}, observed ${observed.get()}"
                }
            }
            scenario("stuck-await-handler-times-out") {
                val handle = sculk.events.await<LoginCheck>(timeoutMillis = 200) { kotlinx.coroutines.awaitCancellation() }
                val started = System.nanoTime()
                val event = proxy.eventManager.fire(LoginCheck("anyone")).await()
                val waitedMillis = (System.nanoTime() - started) / 1_000_000
                handle.close()
                check(event.result.isAllowed && waitedMillis in 150..5_000) { "allowed ${event.result.isAllowed} after $waitedMillis ms" }
            }
            scenario("ready-once-the-proxy-listens") {
                var waited = 0
                while (!sculk.lifecycle.hasReached(LifecyclePhase.READY) && waited++ < 400) delayTicks(1)
                check(sculk.lifecycle.phase == LifecyclePhase.READY) { "phase ${sculk.lifecycle.phase} after ${waited * 50} ms" }
            }
            permissionScenarios.run()
            write()
            proxy.shutdown()
        }
    }

    override fun stop() {
        results["shutdown-reached"] = "PASS"
        write()
    }

    internal suspend fun scenario(name: String, block: suspend () -> String) {
        results[name] = try {
            withTimeout(30.seconds) { block() }
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    internal fun check(condition: Boolean, detail: () -> String): String = if (condition) "PASS" else "FAIL: ${detail()}"

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

    /** A resulted event the suite fires itself, as a login check would be. */
    class LoginCheck(val name: String) : ResultedEvent<ResultedEvent.GenericResult> {
        private var current: ResultedEvent.GenericResult = ResultedEvent.GenericResult.allowed()

        override fun getResult(): ResultedEvent.GenericResult = current

        override fun setResult(result: ResultedEvent.GenericResult) {
            current = result
        }
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
