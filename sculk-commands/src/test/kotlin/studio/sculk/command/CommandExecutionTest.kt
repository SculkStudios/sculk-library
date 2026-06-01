package studio.sculk.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.command.brigadier.CommandCompiler
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.scheduler.SculkScheduler
import java.time.Duration
import java.util.UUID

/**
 * Drives the compiled Brigadier tree through a real [CommandDispatcher] using an inline scheduler,
 * so suspend executors run synchronously within the test.
 */
@OptIn(SculkInternal::class)
class CommandExecutionTest {
    @Test
    fun `dispatch executes nested subcommand with parsed argument`() {
        var amount = 0
        val root =
            command("coins") {
                sub("give") {
                    int("amount", min = 1, max = 10)
                    executes { amount = argument("amount") }
                }
            }.node

        execute(root, "coins give 7", permittedSender())

        assertEquals(7, amount)
    }

    @Test
    fun `permission requirement prevents execution`() {
        var executed = false
        val sender = mock<CommandSender>()
        whenever(sender.hasPermission("coins.admin")).thenReturn(false)
        val root =
            command("coins") {
                permission = "coins.admin"
                executes { executed = true }
            }.node

        assertThrows(CommandSyntaxException::class.java) { execute(root, "coins", sender) }
        assertEquals(false, executed)
    }

    @Test
    fun `cooldown blocks repeated execution`() {
        var executions = 0
        val sender = permittedSender("CooldownSender")
        val root =
            command("ping") {
                cooldown("ping", Duration.ofMinutes(1))
                executes { executions++ }
            }.node
        val dispatcher = dispatcherFor(root)
        val source = sourceFor(sender)

        dispatcher.execute("ping", source)
        dispatcher.execute("ping", source)

        assertEquals(1, executions)
    }

    @Test
    fun `invalid argument value rejects execution`() {
        var executions = 0
        val root =
            command("limit") {
                int("amount", min = 1, max = 10)
                executes { executions++ }
            }.node

        assertThrows(CommandSyntaxException::class.java) { execute(root, "limit wrong", permittedSender()) }
        assertEquals(0, executions)
    }

    @Test
    fun `middleware can abort dispatch`() {
        var executions = 0
        val root =
            command("guarded") {
                middleware { false }
                executes { executions++ }
            }.node

        execute(root, "guarded", permittedSender())

        assertEquals(0, executions)
    }

    @Test
    fun `player executor only runs for players`() {
        var executions = 0
        val player = mock<Player>()
        whenever(player.uniqueId).thenReturn(UUID.randomUUID())
        whenever(player.name).thenReturn("Player")
        val root =
            command("profile") {
                player { executions++ }
            }.node

        execute(root, "profile", player)

        assertEquals(1, executions)
    }

    // --- helpers ---------------------------------------------------------------

    private fun execute(root: CommandNode, input: String, sender: CommandSender) {
        dispatcherFor(root).execute(input, sourceFor(sender))
    }

    private fun dispatcherFor(root: CommandNode): CommandDispatcher<CommandSourceStack> {
        val compiler = CommandCompiler(SculkCoroutineScope(InlineScheduler()))
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.root.addChild(compiler.compile(root))
        return dispatcher
    }

    private fun sourceFor(sender: CommandSender): CommandSourceStack {
        val source = mock<CommandSourceStack>()
        whenever(source.sender).thenReturn(sender)
        return source
    }

    private fun permittedSender(name: String = "Console"): CommandSender {
        val sender = mock<CommandSender>()
        whenever(sender.name).thenReturn(name)
        whenever(sender.hasPermission(org.mockito.kotlin.any<String>())).thenReturn(true)
        return sender
    }

    /** Scheduler that runs tasks inline so coroutine dispatch is synchronous in tests. */
    private class InlineScheduler : SculkScheduler {
        override fun runSync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runSync(task)

        override fun runSync(entity: Entity, task: Runnable): SculkHandle = runSync(task)

        override fun runSync(location: Location, task: Runnable): SculkHandle = runSync(task)

        override fun runAsync(task: Runnable): SculkHandle {
            task.run()
            return SculkHandle {}
        }

        override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle = runAsync(task)

        override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle = runAsync(task)
    }
}
