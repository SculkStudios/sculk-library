package gg.sculk.platform

import gg.sculk.core.SculkHandle
import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.scheduler.SculkScheduler
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit

private const val MILLIS_PER_TICK = 50L

/**
 * Paper/Folia implementation of [SculkScheduler].
 *
 * Automatically detects the server runtime at startup via [FoliaDetector] and
 * routes every method to the correct scheduler:
 *
 * - **Paper** — uses the legacy [org.bukkit.scheduler.BukkitScheduler].
 * - **Folia / Canvas** — uses [io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler]
 *   for generic sync tasks, [io.papermc.paper.threadedregions.scheduler.AsyncScheduler] for
 *   background work, and the [io.papermc.paper.threadedregions.scheduler.EntityScheduler] /
 *   [io.papermc.paper.threadedregions.scheduler.RegionScheduler] overloads for entity- and
 *   location-specific work.
 */
@SculkInternal
public class PaperScheduler(
    private val plugin: Plugin,
) : SculkScheduler {
    // -------------------------------------------------------------------------
    // Sync — global region (equivalent to Paper's main thread)
    // -------------------------------------------------------------------------

    override fun runSync(task: Runnable): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = plugin.server.globalRegionScheduler.run(plugin) { task.run() }
            SculkHandle { t.cancel() }
        } else {
            val t = plugin.server.scheduler.runTask(plugin, task)
            SculkHandle { t.cancel() }
        }

    override fun runSyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = plugin.server.globalRegionScheduler.runDelayed(plugin, { task.run() }, delayTicks)
            SculkHandle { t.cancel() }
        } else {
            val t = plugin.server.scheduler.runTaskLater(plugin, task, delayTicks)
            SculkHandle { t.cancel() }
        }

    override fun runSyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task.run() }, delayTicks, periodTicks)
            SculkHandle { t.cancel() }
        } else {
            val t = plugin.server.scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks)
            SculkHandle { t.cancel() }
        }

    // -------------------------------------------------------------------------
    // Sync — entity region (runs on the thread owning the entity's chunk)
    // On Paper falls back to global scheduler — entity context is ignored.
    // -------------------------------------------------------------------------

    override fun runSync(
        entity: Entity,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = entity.scheduler.run(plugin, { task.run() }, null)
            SculkHandle { t?.cancel() }
        } else {
            runSync(task)
        }

    override fun runSyncDelayed(
        entity: Entity,
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = entity.scheduler.runDelayed(plugin, { task.run() }, null, delayTicks)
            SculkHandle { t?.cancel() }
        } else {
            runSyncDelayed(delayTicks, task)
        }

    // -------------------------------------------------------------------------
    // Sync — location region (runs on the thread owning the location's chunk)
    // On Paper falls back to global scheduler — location context is ignored.
    // -------------------------------------------------------------------------

    override fun runSync(
        location: Location,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = plugin.server.regionScheduler.run(plugin, location) { task.run() }
            SculkHandle { t.cancel() }
        } else {
            runSync(task)
        }

    // -------------------------------------------------------------------------
    // Async — off main/region thread entirely
    // Folia's AsyncScheduler uses real time (ms), not ticks.
    // -------------------------------------------------------------------------

    override fun runAsync(task: Runnable): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t = plugin.server.asyncScheduler.runNow(plugin) { task.run() }
            SculkHandle { t.cancel() }
        } else {
            val t = plugin.server.scheduler.runTaskAsynchronously(plugin, task)
            SculkHandle { t.cancel() }
        }

    override fun runAsyncDelayed(
        delayTicks: Long,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t =
                plugin.server.asyncScheduler.runDelayed(
                    plugin,
                    { task.run() },
                    delayTicks * MILLIS_PER_TICK,
                    TimeUnit.MILLISECONDS,
                )
            SculkHandle { t.cancel() }
        } else {
            val t = plugin.server.scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks)
            SculkHandle { t.cancel() }
        }

    override fun runAsyncRepeating(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): SculkHandle =
        if (FoliaDetector.isFolia) {
            val t =
                plugin.server.asyncScheduler.runAtFixedRate(
                    plugin,
                    { task.run() },
                    delayTicks * MILLIS_PER_TICK,
                    periodTicks * MILLIS_PER_TICK,
                    TimeUnit.MILLISECONDS,
                )
            SculkHandle { t.cancel() }
        } else {
            val t = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks)
            SculkHandle { t.cancel() }
        }
}
