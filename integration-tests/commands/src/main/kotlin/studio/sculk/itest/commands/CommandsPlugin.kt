package studio.sculk.itest.commands

import kotlinx.coroutines.withTimeout
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandException
import studio.sculk.command.CommandDispatcher
import studio.sculk.command.CommandResult
import studio.sculk.command.CommandSource
import studio.sculk.command.CommandSpec
import studio.sculk.command.SenderKind
import studio.sculk.command.command
import studio.sculk.command.paper.commands
import studio.sculk.command.paper.world
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.delayTicks
import studio.sculk.execution.withGlobal
import studio.sculk.paper.SculkPaperPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the same command specs through Paper's Brigadier tree and through Sculk's own interpreter in
 * one server, and checks that every input reaches the same executor with the same values. Writes
 * `results.properties` and stops the server.
 */
class CommandsPlugin : SculkPaperPlugin() {
    private val results = ConcurrentHashMap<String, String>()
    private val recorded = ConcurrentLinkedQueue<String>()

    private val specs: List<CommandSpec> = listOf(
        command("itest-pay") {
            val target = word("player")
            val amount = double("amount", min = 0.01)
            executes { record("pay ${this[target]} ${this[amount]}") }
        },
        command("itest-heal") {
            val amount = integer("amount", min = 1, max = 20).optional(20)
            executes { record("heal ${this[amount]}") }
        },
        command("itest-eco", "itest-economy") {
            literal("give", "add") {
                val amount = long("amount", min = 1)
                executes { record("give ${this[amount]}") }
            }
            literal("reset") { executes { record("reset") } }
        },
        command("itest-mode") {
            val mode = choice("mode", mapOf("survival" to "S", "creative" to "C"))
            executes { record("mode ${this[mode]}") }
        },
        command("itest-note") {
            val title = string("title")
            val body = greedy("body")
            executes { record("note ${this[title]}|${this[body]}") }
        },
        command("itest-mute") {
            val length = duration("length", max = 30.hours)
            val flag = boolean("silent").optional(false)
            executes { record("mute ${this[length]} ${this[flag]}") }
        },
        command("itest-ids") {
            val id = uuid("id")
            val sound = key("sound")
            executes { record("ids ${this[id]} ${this[sound].asString()}") }
        },
        command("itest-code") {
            val code = word("code", maxLength = 4, pattern = Regex("[A-Z0-9]+"))
            executes { record("code ${this[code]}") }
        },
        command("itest-context") {
            executes { record("global ${sculk.scope.execution.executor.owns(ExecutionContext.Global)}") }
        },
    )

    private val permissionScenarios = PermissionScenarios(this)

    private val nativeOnly = command("itest-world") {
        val world = world("world")
        executes { record("world ${this[world].name}") }
    }

    /** Inputs both paths must agree on, valid and invalid. */
    private val inputs = listOf(
        "itest-pay Alex 12.5",
        "itest-pay Alex 0",
        "itest-pay Alex",
        "itest-heal",
        "itest-heal 7",
        "itest-heal 21",
        "itest-eco give 30",
        "itest-economy add 5",
        "itest-eco reset",
        "itest-eco take 5",
        "itest-mode CREATIVE",
        "itest-mode adventure",
        "itest-note \"shopping list\" eggs and milk",
        "itest-note plain body text here",
        "itest-mute 1h30m",
        "itest-mute 90s true",
        "itest-mute 31h",
        "itest-ids 123e4567-e89b-12d3-a456-426614174000 block.note_block.bell",
        "itest-ids not-a-uuid minecraft:stone",
        "itest-code AB12",
        "itest-code toolong",
        "itest-code ab",
    )

    override suspend fun start() {
        val commands = sculk.commands()
        commands.register(*specs.toTypedArray())
        commands.register(nativeOnly)
        commands.register(*permissionScenarios.specs.toTypedArray())
        permissionScenarios.register()
        sculk.scope.launch {
            delayTicks(20)
            scenario("late-registration-is-refused") {
                try {
                    commands.register(command("itest-late") { executes {} })
                    "FAIL: a command registered after the tree was built was accepted"
                } catch (_: IllegalStateException) {
                    "PASS"
                }
            }
            scenario("paper-and-interpreter-agree") { conformance() }
            scenario("native-world-argument") {
                val world = server.worlds.first()
                val outcome = runOnPaper("itest-world ${world.key.asString()}")
                check(outcome == "world ${world.name}") { "recorded '$outcome'" }
            }
            scenario("console-commands-start-on-global") {
                val outcome = runOnPaper("itest-context")
                check(outcome == "global true") { "recorded '$outcome'" }
            }
            permissionScenarios.run()
            write()
            withGlobal { server.shutdown() }
        }
    }

    private suspend fun conformance(): String {
        val interpreter = CommandDispatcher().apply { specs.forEach(::register) }
        val mismatches = inputs.mapNotNull { input ->
            val paper = runOnPaper(input)
            val sculkOutcome = interpreter.execute(ConsoleSource, input)
            val interpreted = recorded.poll().also { recorded.clear() }
            val agree = paper == interpreted && (sculkOutcome == CommandResult.Success) == (interpreted != null)
            if (agree) null else "'$input': paper '$paper', interpreter '$interpreted' (${sculkOutcome::class.simpleName})"
        }
        return if (mismatches.isEmpty()) "PASS (${inputs.size} inputs)" else "FAIL: ${mismatches.joinToString("; ")}"
    }

    /**
     * Dispatches [input] as the console on the global region and returns what the executor recorded,
     * or null. Paper 26 throws syntax errors out of dispatchCommand where 1.21 only reports them to
     * the sender; both mean the executor did not run.
     */
    internal suspend fun runOnPaper(input: String): String? {
        recorded.clear()
        withGlobal {
            try {
                server.dispatchCommand(server.consoleSender, input)
            } catch (_: CommandException) {
                false
            }
        }
        withGlobal { delayTicks(2) }
        return recorded.poll().also { recorded.clear() }
    }

    internal fun record(outcome: String) {
        recorded += outcome
    }

    internal suspend fun scenario(name: String, block: suspend () -> String) {
        results[name] = try {
            withTimeout(60.seconds) { block() }
        } catch (failure: Throwable) {
            "FAIL: " + generateSequence(failure) { it.cause }.joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" } +
                " at " + generateSequence(failure) { it.cause }.last().stackTrace.take(6).joinToString(" | ")
        }
    }

    internal fun check(condition: Boolean, detail: () -> String): String = if (condition) "PASS" else "FAIL: ${detail()}"

    @Synchronized
    private fun write() {
        val file = File(dataFolder, "results.properties").also { it.parentFile.mkdirs() }
        file.writeText(results.toSortedMap().entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
    }

    /** The console as the interpreter sees it: every permission, messages discarded. */
    private object ConsoleSource : CommandSource {
        override val name: String = "Console"
        override val kind: SenderKind = SenderKind.CONSOLE
        override val uniqueId: UUID? = null
        override val audience: Audience = object : Audience {
            override fun sendMessage(message: Component) = Unit
        }
        override val executionContext: ExecutionContext = ExecutionContext.Global
        override val platformSender: Any = this

        override fun hasPermission(permission: String): Boolean = true
    }
}
