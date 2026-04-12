package gg.sculk.benchmarks

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.command.CommandExecutor
import gg.sculk.core.command.CommandNode
import gg.sculk.core.command.command
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
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
    private lateinit var root: CommandNode
    private val sender: CommandSender = NoopSender

    @Setup(Level.Trial)
    public fun setup() {
        root =
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
    }

    @Benchmark
    public fun dispatchPing(): Unit = CommandExecutor.dispatch(root, sender, "sculk", arrayOf("ping"))

    @Benchmark
    public fun dispatchGiveWithArgs(): Unit = CommandExecutor.dispatch(root, sender, "sculk", arrayOf("give", "Steve", "64"))

    @Benchmark
    public fun dispatchUnknownSub(): Unit = CommandExecutor.dispatch(root, sender, "sculk", arrayOf("unknown"))
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
