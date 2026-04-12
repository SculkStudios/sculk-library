package gg.sculk.platform

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.scheduler.SculkScheduler
import org.bukkit.plugin.Plugin

/**
 * Paper implementation of [SculkScheduler] backed by the Bukkit task scheduler.
 */
@SculkInternal
public class PaperScheduler(
    private val plugin: Plugin,
) : SculkScheduler {
    override fun runSync(task: Runnable): SculkHandle {
        val t = plugin.server.scheduler.runTask(plugin, task)
        return SculkHandle { t.cancel() }
    }

    override fun runSyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle {
        val t = plugin.server.scheduler.runTaskLater(plugin, task, delayTicks)
        return SculkHandle { t.cancel() }
    }

    override fun runSyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle {
        val t = plugin.server.scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks)
        return SculkHandle { t.cancel() }
    }

    override fun runAsync(task: Runnable): SculkHandle {
        val t = plugin.server.scheduler.runTaskAsynchronously(plugin, task)
        return SculkHandle { t.cancel() }
    }

    override fun runAsyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle {
        val t = plugin.server.scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks)
        return SculkHandle { t.cancel() }
    }

    override fun runAsyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle {
        val t = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
        return SculkHandle { t.cancel() }
    }
}
