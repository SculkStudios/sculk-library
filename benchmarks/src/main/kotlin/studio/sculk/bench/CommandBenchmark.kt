package studio.sculk.bench

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.runBlocking
import studio.sculk.command.CommandDispatcher
import studio.sculk.command.CommandResult
import studio.sculk.command.command
import studio.sculk.testkit.FakeCommandSource

/**
 * Interpreting a typical command: a literal branch, two typed arguments, a permission check and an
 * executor that does nothing, so the number is Sculk's own overhead. The executor never suspends,
 * so runBlocking adds only its fixed entry cost.
 */
@State(Scope.Benchmark)
class CommandBenchmark {
    private lateinit var dispatcher: CommandDispatcher
    private val console = FakeCommandSource.console()

    @Setup
    fun setUp() {
        dispatcher = CommandDispatcher()
        dispatcher.register(
            command("economy", "eco") {
                permission = "economy.admin"
                literal("give") {
                    val player = word("player")
                    val amount = double("amount", min = 0.01)
                    executes {
                        this[player]
                        this[amount]
                    }
                }
                literal("take") { word("player").also { executes {} } }
            },
        )
    }

    @Benchmark
    fun execute(): CommandResult = runBlocking { dispatcher.execute(console, "eco give Steve 125.5") }

    @Benchmark
    fun suggest(): List<String> = dispatcher.suggest(console, "eco gi")
}
