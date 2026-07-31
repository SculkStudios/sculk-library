package studio.sculk.platform

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.scheduler.SculkScheduler
import java.util.concurrent.TimeUnit

private const val MILLIS_PER_TICK = 50L

/**
 * The real scheduler, on Paper's region scheduler API.
 *
 * ### One code path, not two
 *
 * The previous implementation branched on `isFolia` in all twelve methods: two implementations to
 * keep in step, only one of them ever exercised on a given server. Paper implements the region
 * scheduler API natively, so the region calls are correct on both and the branch bought nothing
 * except the opportunity for the two halves to drift apart.
 *
 * ### The three sharp edges
 *
 * Folia rejects a non-positive delay *and* a non-positive period, and `runDelayed` throws rather
 * than treating zero as "next tick". A caller computing a delay from a config value should get a
 * scheduled task, not an exception, so both are normalised here instead of at every call site.
 *
 * The third is quieter: the entity scheduler takes a *retired* callback for when the entity is
 * gone before the task runs. Passing null drops the work silently, which is the shape of bug that
 * ends up blamed on the database.
 */
@SculkInternal
public class PaperScheduler(private val plugin: JavaPlugin) : SculkScheduler {
    override fun runSync(task: Runnable): SculkHandle {
        val scheduled = Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSyncDelayed(delayTicks: Long, task: Runnable): SculkHandle {
        if (delayTicks <= 0) return runSync(task)
        val scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delayTicks)
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle {
        val scheduled = Bukkit.getGlobalRegionScheduler()
            .runAtFixedRate(plugin, { task.run() }, delayTicks.atLeastOneTick(), periodTicks.atLeastOneTick())
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSync(entity: Entity, task: Runnable): SculkHandle {
        val scheduled = entity.scheduler.run(plugin, { task.run() }, retired(task)) ?: return SculkHandle.NONE
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSyncDelayed(entity: Entity, delayTicks: Long, task: Runnable): SculkHandle {
        if (delayTicks <= 0) return runSync(entity, task)
        val scheduled = entity.scheduler.runDelayed(plugin, { task.run() }, retired(task), delayTicks)
            ?: return SculkHandle.NONE
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSyncRepeating(entity: Entity, delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle {
        val scheduled = entity.scheduler.runAtFixedRate(
            plugin,
            { task.run() },
            retired(task),
            delayTicks.atLeastOneTick(),
            periodTicks.atLeastOneTick(),
        ) ?: return SculkHandle.NONE
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSync(location: Location, task: Runnable): SculkHandle {
        val scheduled = Bukkit.getRegionScheduler().run(plugin, location) { task.run() }
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSyncDelayed(location: Location, delayTicks: Long, task: Runnable): SculkHandle {
        if (delayTicks <= 0) return runSync(location, task)
        val scheduled = Bukkit.getRegionScheduler().runDelayed(plugin, location, { task.run() }, delayTicks)
        return SculkHandle { scheduled.cancel() }
    }

    override fun runSyncRepeating(location: Location, delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle {
        val scheduled = Bukkit.getRegionScheduler()
            .runAtFixedRate(plugin, location, { task.run() }, delayTicks.atLeastOneTick(), periodTicks.atLeastOneTick())
        return SculkHandle { scheduled.cancel() }
    }

    override fun ownsThread(entity: Entity): Boolean = Bukkit.isOwnedByCurrentRegion(entity)

    override fun ownsThread(location: Location): Boolean = Bukkit.isOwnedByCurrentRegion(location)

    /**
     * Whether this is the global thread.
     *
     * On Folia `isPrimaryThread()` means "some region thread", not "the global one", so it has to
     * be asked the specific question. The tempting shortcut — `!isFolia && isPrimaryThread()` — is
     * permanently false on Folia, which makes every `runNow` there schedule a task and cost a tick
     * even when the caller was already on the right thread.
     */
    override fun ownsGlobalThread(): Boolean = if (ServerFlavour.isFolia) Bukkit.isGlobalTickThread() else Bukkit.isPrimaryThread()

    override fun runAsync(task: Runnable): SculkHandle {
        val scheduled = Bukkit.getAsyncScheduler().runNow(plugin) { task.run() }
        return SculkHandle { scheduled.cancel() }
    }

    override fun runAsyncDelayed(delayTicks: Long, task: Runnable): SculkHandle {
        val scheduled = Bukkit.getAsyncScheduler().runDelayed(
            plugin,
            { task.run() },
            delayTicks.atLeastOneTick() * MILLIS_PER_TICK,
            TimeUnit.MILLISECONDS,
        )
        return SculkHandle { scheduled.cancel() }
    }

    override fun runAsyncRepeating(delayTicks: Long, periodTicks: Long, task: Runnable): SculkHandle {
        // The async scheduler speaks durations, not ticks; callers speak ticks everywhere else, so
        // the conversion lives here rather than in every caller.
        val scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(
            plugin,
            { task.run() },
            delayTicks.atLeastOneTick() * MILLIS_PER_TICK,
            periodTicks.atLeastOneTick() * MILLIS_PER_TICK,
            TimeUnit.MILLISECONDS,
        )
        return SculkHandle { scheduled.cancel() }
    }

    /**
     * What to do when the entity is gone before the task ran.
     *
     * Null here silently drops the work. For a save-on-quit that is data loss with no log line, so
     * the task runs anyway: it was scheduled against an entity for thread affinity, and the entity
     * being gone does not mean the work stopped mattering.
     */
    private fun retired(task: Runnable) = Runnable { task.run() }

    private fun Long.atLeastOneTick(): Long = if (this < 1) 1 else this
}

/**
 * Whether this server splits the world across region threads.
 *
 * Resolved once at class load: the answer cannot change while the server is up, and it is read
 * from [PaperScheduler.ownsGlobalThread] on hot paths.
 */
@SculkInternal
public object ServerFlavour {
    public val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
    }.isSuccess
}
