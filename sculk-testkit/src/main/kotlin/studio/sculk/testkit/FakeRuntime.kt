package studio.sculk.testkit

import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.getOrThrow
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.runtime.AbstractSculkRuntime
import studio.sculk.runtime.PlatformInfo
import studio.sculk.runtime.SculkLog
import studio.sculk.time.SculkClock
import studio.sculk.version.MinecraftVersion

/**
 * A [studio.sculk.runtime.SculkRuntime] for unit tests. Its executor is a [FakeExecutor], so
 * nothing runs until the test ticks, and ownership is checked the way the chosen platform would.
 *
 * ```kotlin
 * val runtime = FakeRuntime(platform = FakeRuntime.folia())
 * runtime.start()
 * val result = runtime.executor.run { myFeature.doWork() }
 * ```
 */
@SculkStable
public class FakeRuntime(
    name: String = "TestPlugin",
    platform: PlatformInfo = paper(),
    public val executor: FakeExecutor = FakeExecutor(modelFor(platform)),
    public val logged: RecordingLog = RecordingLog(),
    developmentMode: Boolean = true,
    clock: SculkClock = SculkClock.SYSTEM,
    private val capabilitySetup: Capabilities.Builder.() -> Unit = {},
) : AbstractSculkRuntime(name, platform, executor, logged, developmentMode, clock) {
    /** Moves the runtime through every phase up to READY, as a server would on enable. */
    public fun start() {
        listOf(LifecyclePhase.CONFIGURE, LifecyclePhase.LOAD, LifecyclePhase.START).forEach(lifecycle::advance)
        resolveCapabilities(Capabilities.Builder().apply(capabilitySetup).build())
        lifecycle.advance(LifecyclePhase.READY)
    }

    @SculkStable
    public companion object {
        public fun paper(version: String = "26.3"): PlatformInfo =
            PlatformInfo(PlatformInfo.Kind.PAPER, "Fake Paper", MinecraftVersion.parse(version).getOrThrow())

        public fun folia(version: String = "26.2"): PlatformInfo =
            PlatformInfo(PlatformInfo.Kind.FOLIA, "Fake Folia", MinecraftVersion.parse(version).getOrThrow())

        public fun canvas(version: String = "26.3"): PlatformInfo =
            PlatformInfo(PlatformInfo.Kind.CANVAS, "Fake Canvas", MinecraftVersion.parse(version).getOrThrow())

        public fun velocity(): PlatformInfo = PlatformInfo(PlatformInfo.Kind.VELOCITY, "Fake Velocity", null)

        private fun modelFor(platform: PlatformInfo): FakeExecutor.ThreadModel = when {
            platform.kind.isProxy -> FakeExecutor.ThreadModel.PROXY
            platform.kind.isRegionized -> FakeExecutor.ThreadModel.REGIONIZED
            else -> FakeExecutor.ThreadModel.SINGLE_THREAD
        }
    }
}

/** A [SculkLog] that keeps every line for assertions. Thread-safe. */
@SculkStable
public class RecordingLog : SculkLog {
    /** One logged line: its level, message and cause. */
    @SculkStable
    public data class Entry(public val level: Level, public val message: String, public val cause: Throwable?)

    @SculkStable
    public enum class Level { INFO, WARN, ERROR }

    private val lock = Any()
    private val recorded = mutableListOf<Entry>()

    public val entries: List<Entry> get() = synchronized(lock) { recorded.toList() }

    public val errors: List<Entry> get() = entries.filter { it.level == Level.ERROR }

    override fun info(message: String): Unit = record(Level.INFO, message, null)

    override fun warn(message: String, cause: Throwable?): Unit = record(Level.WARN, message, cause)

    override fun error(message: String, cause: Throwable?): Unit = record(Level.ERROR, message, cause)

    private fun record(level: Level, message: String, cause: Throwable?) {
        synchronized(lock) { recorded += Entry(level, message, cause) }
    }
}
