package studio.sculk.platform

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.command.CommandCooldowns
import studio.sculk.config.SculkConfig
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.data.SculkData
import studio.sculk.data.StorageSettings
import studio.sculk.getOrElse
import studio.sculk.gui.MenuListener
import studio.sculk.gui.MenuRegistry
import studio.sculk.hud.HudService
import studio.sculk.packets.PacketServiceConfig
import studio.sculk.packets.SculkPacketService
import studio.sculk.packets.SculkPacketServices
import studio.sculk.scheduler.SculkScheduler
import studio.sculk.task.SculkTasks
import studio.sculk.text.SculkBundles
import studio.sculk.text.SculkMessages
import studio.sculk.text.SculkTheme
import java.io.File

/**
 * Everything a plugin gets, wired once.
 *
 * ### Subsystems are lazy
 *
 * A plugin that never touches the database should not open a connection pool, and the previous
 * design made that the caller's problem: subsystems were opt-in flags on a builder, and reaching
 * one you had not declared threw with "call data() in SculkPlatform.create". Here they are simply
 * `by lazy` — using `data` opens it, not using it costs nothing, and there is no third state where
 * you asked for something and forgot to declare it.
 *
 * The `…Opened` flags exist so shutdown does not touch a lazy that was never used. Without them,
 * closing the platform would *open* a connection pool purely in order to close it.
 */
@SculkStable
public class SculkPlatform
@SculkInternal
constructor(
    private val plugin: JavaPlugin,
    /** The palette every message renders against. */
    public val theme: SculkTheme = SculkTheme.EMPTY,
    shadowArgb: Int? = null,
) : SculkHandle {
    @SculkStable
    public val scheduler: SculkScheduler = PaperScheduler(plugin)

    @SculkStable
    public val scope: SculkCoroutineScope = SculkCoroutineScope(scheduler, plugin.name)

    @SculkStable
    public val messages: SculkMessages = SculkMessages(theme, shadowArgb)

    /** Type-keyed store for the plugin's own services. Explicitly not DI — see [ServiceRegistry]. */
    @SculkStable
    public val services: ServiceRegistry = ServiceRegistry()

    @SculkStable
    public val events: SculkEventBus = SculkEventBus(plugin)

    @SculkStable
    public val tasks: SculkTasks = SculkTasks(scope)

    @SculkStable
    public val cooldowns: CommandCooldowns = CommandCooldowns()

    @SculkStable
    public val config: SculkConfig by lazy { SculkConfig(plugin.dataFolder, plugin.logger) }

    @SculkStable
    public val menus: MenuRegistry by lazy { MenuRegistry(scheduler, scope, messages) }

    @SculkStable
    public val hud: HudService by lazy {
        hudStarted = true
        HudService(scheduler, messages).also { it.start() }
    }

    @SculkStable
    public val bundles: SculkBundles by lazy {
        SculkBundles(File(plugin.dataFolder, "lang").apply { mkdirs() }, messages, plugin.logger)
    }

    /**
     * The database.
     *
     * A bad `storage.yml` logs and falls back to SQLite rather than refusing to enable: a server
     * that boots with the wrong backend and says so is recoverable; one that will not start is a
     * support ticket at midnight.
     */
    @SculkStable
    public val data: SculkData by lazy {
        dataOpened = true
        val settings = config.load<StorageSettings>().getOrNull() ?: StorageSettings()
        SculkData.open(settings, plugin.dataFolder, plugin.logger).getOrElse { message, _ ->
            plugin.logger.warning("[Sculk] $message Falling back to SQLite.")
            SculkData.open(StorageSettings(backend = "sqlite"), plugin.dataFolder, plugin.logger).getOrThrow()
        }
    }

    /**
     * The packet backend, if one is installed.
     *
     * Absent is not an error. A plugin that wants client-side blocks on a server without
     * PacketEvents still enables; the calls report why they cannot work.
     */
    @SculkStable
    public val packets: SculkResult<SculkPacketService> by lazy {
        SculkPacketServices.create(plugin, scheduler, PacketServiceConfig())
    }

    private var dataOpened = false
    private var hudStarted = false

    internal fun start() {
        // Unconditional. When this was opt-in, a plugin that opened a menu without enabling the
        // subsystem got an inventory whose clicks were never cancelled.
        events.listen(MenuListener(menus))
        RebuildWarning.check(plugin)
    }

    override fun close() {
        // The scope first: nothing should be part-way through touching a subsystem while it is
        // being torn down.
        scope.close()
        if (hudStarted) hud.close()
        menus.close()
        events.close()
        services.close()
        if (dataOpened) data.close()
    }

    /** Reports which subsystems were actually used. Exposed so a test can assert shutdown is lazy. */
    @SculkInternal
    public val opened: Set<String>
        get() = buildSet {
            if (dataOpened) add("data")
            if (hudStarted) add("hud")
        }
}
