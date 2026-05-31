package studio.sculk.benchmarks

import com.mojang.brigadier.CommandDispatcher
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.command.brigadier.CommandCompiler
import studio.sculk.command.command
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.scheduler.SculkScheduler
import java.util.concurrent.TimeUnit

/**
 * Benchmarks the command dispatch hot path: subcommand lookup, argument parsing, executor invocation.
 *
 * Target: < 1 μs per dispatch.
 */
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@OptIn(SculkInternal::class)
public open class CommandDispatchBenchmark {
    private lateinit var dispatcher: CommandDispatcher<CommandSourceStack>
    private lateinit var source: CommandSourceStack

    @Setup(Level.Trial)
    public fun setup() {
        val root =
            command("sculk") {
                sub("ping") {
                    executes { /* no-op */ }
                }
                sub("give") {
                    string("target")
                    int("amount")
                    executes { /* no-op */ }
                }
            }.node
        val compiler = CommandCompiler(SculkCoroutineScope(InlineScheduler()))
        dispatcher = CommandDispatcher()
        dispatcher.root.addChild(compiler.compile(root))
        source = mock<CommandSourceStack>().also { whenever(it.sender).thenReturn(NoopSender) }
    }

    @Benchmark
    public fun dispatchPing(): Unit = run { dispatcher.execute("sculk ping", source) }

    @Benchmark
    public fun dispatchGiveWithArgs(): Unit = run { dispatcher.execute("sculk give Steve 64", source) }

    @Benchmark
    public fun dispatchUnknownSub(): Unit = runCatching { dispatcher.execute("sculk unknown", source) }.let { }
}

/** Scheduler that runs tasks inline — keeps benchmark dispatch synchronous. */
private class InlineScheduler : SculkScheduler {
    override fun runSync(task: Runnable): SculkHandle {
        task.run()
        return SculkHandle {}
    }

    override fun runSyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle = runSync(task)

    override fun runSyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle = runSync(task)

    override fun runSync(
        entity: Entity,
        task: Runnable,
    ): SculkHandle = runSync(task)

    override fun runSync(
        location: Location,
        task: Runnable,
    ): SculkHandle = runSync(task)

    override fun runAsync(task: Runnable): SculkHandle {
        task.run()
        return SculkHandle {}
    }

    override fun runAsyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle = runAsync(task)

    override fun runAsyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle = runAsync(task)
}

/** Minimal CommandSender that discards all output — avoids Adventure overhead in benchmarks. */
private object NoopSender : CommandSender {
    override fun sendMessage(message: String): Unit = Unit

    override fun sendMessage(vararg messages: String): Unit = Unit

    override fun sendMessage(
        sender: java.util.UUID?,
        message: String,
    ): Unit = Unit

    override fun sendMessage(
        sender: java.util.UUID?,
        vararg messages: String,
    ): Unit = Unit

    override fun getServer() = throw UnsupportedOperationException()

    override fun getName() = "Benchmark"

    override fun spigot() = throw UnsupportedOperationException()

    override fun isPermissionSet(name: String) = true

    override fun isPermissionSet(perm: Permission) = true

    override fun hasPermission(name: String) = true

    override fun hasPermission(perm: Permission) = true

    override fun addAttachment(plugin: Plugin): PermissionAttachment = throw UnsupportedOperationException()

    override fun addAttachment(
        plugin: Plugin,
        name: String,
        value: Boolean,
    ): PermissionAttachment = throw UnsupportedOperationException()

    override fun addAttachment(
        plugin: Plugin,
        ticks: Int,
    ): PermissionAttachment? = null

    override fun addAttachment(
        plugin: Plugin,
        name: String,
        value: Boolean,
        ticks: Int,
    ): PermissionAttachment? = null

    override fun removeAttachment(attachment: PermissionAttachment): Unit = Unit

    override fun recalculatePermissions(): Unit = Unit

    override fun getEffectivePermissions(): Set<PermissionAttachmentInfo> = emptySet()

    override fun isOp(): Boolean = true

    override fun setOp(value: Boolean): Unit = Unit

    override fun name(): net.kyori.adventure.text.Component =
        net.kyori.adventure.text.Component
            .text("Benchmark")
}
