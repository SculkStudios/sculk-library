package studio.sculk.paper

import kotlinx.coroutines.CoroutineStart
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.annotation.SculkStable
import studio.sculk.execution.ExecutionContext
import studio.sculk.execution.SculkExecutor
import kotlin.coroutines.cancellation.CancellationException

/**
 * A plugin managed by Sculk: the runtime is created before [start] and closed after [stop].
 *
 * [start] begins on the enabling thread and runs synchronously until its first suspension, so
 * commands and listeners registered before then are in place when `onEnable` returns. If it fails,
 * the failure is logged and the plugin disables itself rather than running half-initialised.
 */
@SculkStable
public abstract class SculkPaperPlugin : JavaPlugin() {
    private var runtime: PaperRuntime? = null

    /** The runtime; available from [start] until the plugin disables. */
    public val sculk: PaperRuntime get() = checkNotNull(runtime) { "$name has no Sculk runtime: it is not enabled." }

    /** Whether development checks run for this plugin. Defaults to the `sculk.development` system property. */
    protected open val developmentMode: Boolean get() = Sculk.developmentModeByDefault()

    /** Platform extensions to attach, such as Canvas support. */
    protected open val platformExtensions: List<PaperPlatformExtension> get() = emptyList()

    /** Registers the plugin's commands, listeners and tasks. */
    protected abstract suspend fun start()

    /** Runs before the runtime closes. Must not suspend or schedule: no task runs after shutdown begins. */
    protected open fun stop() {}

    final override fun onEnable() {
        val created = Sculk.attach(this, developmentMode, platformExtensions)
        runtime = created
        val starting = created.scope.launch(start = CoroutineStart.UNDISPATCHED) { start() }
        starting.invokeOnCompletion { failure ->
            if (failure == null || failure is CancellationException) return@invokeOnCompletion
            created.log.error("$name failed to start and will be disabled.", failure)
            created.scope.execution.executor.schedule(
                ExecutionContext.Global,
                1,
                object : SculkExecutor.Work {
                    override fun run() = server.pluginManager.disablePlugin(this@SculkPaperPlugin)
                },
            )
        }
    }

    final override fun onDisable() {
        val current = runtime ?: return
        try {
            stop()
        } finally {
            current.close()
            runtime = null
        }
    }
}
