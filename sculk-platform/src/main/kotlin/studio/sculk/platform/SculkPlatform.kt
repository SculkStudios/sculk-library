package studio.sculk.platform

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.config.SculkConfig
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.data.SculkData
import studio.sculk.event.SculkEventBus
import studio.sculk.gui.GuiContext
import studio.sculk.gui.GuiRegistry
import studio.sculk.integrations.SculkIntegrations
import studio.sculk.packets.PacketServiceConfig
import studio.sculk.packets.SculkPacketService
import studio.sculk.packets.SculkPacketServices
import studio.sculk.platform.command.SculkCommandBridge
import studio.sculk.scheduler.SculkScheduler
import studio.sculk.tasks.SculkTasks
import studio.sculk.text.SculkText
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * The Sculk Studio platform — the single entry point that wires all modules together.
 *
 * Create one instance in `onEnable` and close it in `onDisable`.
 *
 * ```kotlin
 * class MyPlugin : JavaPlugin() {
 *     lateinit var sculk: SculkPlatform
 *
 *     override fun onEnable() {
 *         sculk = SculkPlatform.create(this) {
 *             gui()
 *             config()
 *             data()
 *         }
 *         sculk.commands.register(command("hello") {
 *             player { reply("<green>Hello!") }
 *         })
 *     }
 *
 *     override fun onDisable() = sculk.close()
 * }
 * ```
 */
@SculkStable
public class SculkPlatform internal constructor(
    private val plugin: JavaPlugin,
    /** The scheduler for running sync and async tasks. */
    public val scheduler: SculkScheduler,
    /** Plugin-lifecycle-bound coroutine scope for structured concurrency. */
    public val scope: SculkCoroutineScope,
    /** The event bus for registering auto-cleaned listeners. */
    public val events: SculkEventBus,
    /** Coroutine scheduling helpers: repeating tasks, cron, debounce/throttle. */
    public val tasks: SculkTasks,
    /** Command registration bridge. Use [commands.register] to add commands. */
    public val commands: SculkCommandBridge,
    private val configService: SculkConfig?,
    private val dataService: SculkData?,
    private val textService: SculkText?,
    private val integrationService: SculkIntegrations?,
    private val packetServiceResult: SculkResult<SculkPacketService>?,
    private val handles: List<SculkHandle>,
) : SculkHandle {
    private val closed = AtomicBoolean(false)

    /** Config system. Requires [SculkPlatformBuilder.config] during platform creation. */
    public val config: SculkConfig
        get() = configService ?: missingSubsystem("config", "config()")

    /** Data layer. Requires [SculkPlatformBuilder.data] during platform creation. */
    public val data: SculkData
        get() = dataService ?: missingSubsystem("data", "data()")

    /** Localization. Requires [SculkPlatformBuilder.text] during platform creation. */
    public val text: SculkText
        get() = textService ?: missingSubsystem("text", "text()")

    /** Optional integration adapters. Requires [SculkPlatformBuilder.integrations]. */
    public val integrations: SculkIntegrations
        get() = integrationService ?: missingSubsystem("integrations", "integrations()")

    /** Packet service. Requires [SculkPlatformBuilder.packets] and a successful backend. */
    public val packets: SculkPacketService
        get() =
            when (val result = packetServiceResult) {
                is SculkResult.Success -> result.value
                is SculkResult.Failure -> throw IllegalStateException(result.message, result.cause)
                null -> missingSubsystem("packets", "packets { ... }")
            }

    /** Packet backend result for plugins that can run without packet support. */
    public val packetsResult: SculkResult<SculkPacketService>?
        get() = packetServiceResult

    /**
     * Tears down all Sculk components in reverse-registration order.
     *
     * Unregisters all event listeners, commands, closes GUI sessions, and shuts down the data pool.
     * Safe to call multiple times.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Cancel running coroutines first so nothing touches subsystems mid-teardown.
        scope.close()
        handles.asReversed().forEach { it.close() }
        GuiRegistry.closeAll()
        commands.close()
        events.close()
        dataService?.close()
    }

    private fun <T> missingSubsystem(name: String, call: String): T =
        throw IllegalStateException("Sculk $name subsystem is not enabled. Call $call in SculkPlatform.create { ... }.")

    public companion object {
        /**
         * Creates a [SculkPlatform] for [plugin] with the given configuration.
         *
         * ```kotlin
         * sculk = SculkPlatform.create(this) {
         *     gui()
         *     config()
         *     data()
         * }
         * ```
         */
        @JvmStatic
        @JvmOverloads
        @SculkStable
        public fun create(plugin: JavaPlugin, block: SculkPlatformBuilder.() -> Unit = {}): SculkPlatform =
            SculkPlatformBuilder(plugin).apply(block).build()

        /**
         * Java-friendly overload of [create] taking a [Consumer].
         *
         * ```java
         * sculk = SculkPlatform.create(this, b -> { b.gui(); b.config(); b.data(); });
         * ```
         */
        @JvmStatic
        @SculkStable
        public fun create(plugin: JavaPlugin, block: Consumer<SculkPlatformBuilder>): SculkPlatform =
            SculkPlatformBuilder(plugin).also { block.accept(it) }.build()
    }
}

/**
 * DSL builder for [SculkPlatform].
 */
@SculkStable
public class SculkPlatformBuilder(private val plugin: JavaPlugin) {
    private var configEnabled = false
    private var dataEnabled = false
    private var textEnabled = false
    private var guiEnabled = false
    private var integrationsEnabled = false
    private var packetConfig: PacketServiceConfig? = null

    /** Enables the config system (auto-loads configs from [JavaPlugin.getDataFolder]). */
    @SculkStable
    public fun config() {
        configEnabled = true
    }

    /** Enables the data layer (SQLite by default, config via storage.yml). */
    @SculkStable
    public fun data() {
        dataEnabled = true
    }

    /** Enables localization, loading message bundles from `<dataFolder>/lang`. */
    @SculkStable
    public fun text() {
        textEnabled = true
    }

    /** Registers GUI event listeners (inventory click, close, player quit routing). */
    @SculkStable
    public fun gui() {
        guiEnabled = true
    }

    /** Enables optional integration adapters such as PlaceholderAPI, Vault, and LuckPerms. */
    @SculkStable
    public fun integrations() {
        integrationsEnabled = true
    }

    /** Enables optional packet APIs. PacketEvents is preferred when the backend mode is Auto. */
    @JvmOverloads
    @SculkStable
    public fun packets(block: PacketServiceConfig.() -> Unit = {}) {
        packetConfig = PacketServiceConfig().apply(block)
    }

    /** Java-friendly overload of [packets] taking a [Consumer]. */
    @SculkStable
    public fun packets(block: Consumer<PacketServiceConfig>) {
        packetConfig = PacketServiceConfig().also { block.accept(it) }
    }

    @OptIn(studio.sculk.annotation.SculkInternal::class)
    internal fun build(): SculkPlatform {
        val scheduler = PaperScheduler(plugin)
        val scope = SculkCoroutineScope(scheduler, plugin.name)
        val events = SculkEventBus(plugin)
        val tasks = SculkTasks(scope)
        val commands = SculkCommandBridge(plugin, scope)
        val extraHandles = mutableListOf<SculkHandle>()

        // Wire Folia-aware GUI dispatch — done unconditionally so Gui.openFor() works
        // even when the gui() subsystem is not enabled for event routing.
        @OptIn(studio.sculk.annotation.SculkInternal::class)
        GuiRegistry.init(plugin, FoliaDetector.isFolia, scope)

        // Config
        val sculkConfig =
            if (configEnabled) {
                SculkConfig.create(plugin.dataFolder, plugin.logger)
            } else {
                null
            }

        // Data
        val sculkData =
            if (dataEnabled) {
                SculkData.create(plugin.dataFolder, plugin.logger)
            } else {
                null
            }

        val sculkText = if (textEnabled) SculkText.create(plugin.dataFolder, plugin.logger) else null

        val sculkIntegrations = if (integrationsEnabled) SculkIntegrations(plugin) else null

        val sculkPackets =
            packetConfig?.let { config ->
                SculkPacketServices.create(plugin, scheduler, config).also { result ->
                    when (result) {
                        is SculkResult.Success -> extraHandles += result.value

                        is SculkResult.Failure ->
                            if (config.required) {
                                throw IllegalStateException(result.message, result.cause)
                            }
                    }
                }
            }

        // GUI event routing
        if (guiEnabled) {
            extraHandles +=
                events.listen<InventoryClickEvent> { event ->
                    val session = GuiRegistry.sessionForInventory(event.view.topInventory) ?: return@listen
                    val player = event.whoClicked as? Player ?: return@listen
                    val item = session.gui.items[event.rawSlot]

                    // Lock the slot unless it is an explicit interactive (input) slot.
                    if (item == null || !item.interactive) event.isCancelled = true
                    if (item == null) return@listen

                    val handler = item.resolveHandler(event.click) ?: return@listen

                    @OptIn(studio.sculk.annotation.SculkInternal::class)
                    val ctx = GuiContext(player, event.click, event, session)
                    handler(ctx)
                    // Handle pending GUI switch (opened via ctx.open())
                    val pending = session.pendingSwitch()
                    if (pending != null) pending.openFor(player)
                }

            extraHandles +=
                events.listen<InventoryCloseEvent> { event ->
                    val player = event.player as? Player ?: return@listen

                    // Use inventory identity — prevents unregistering a *new* session when
                    // openFor() is called during a click handler (the new session is already
                    // registered under the player's UUID before the old inventory fires its close event).
                    @OptIn(studio.sculk.annotation.SculkInternal::class)
                    val session = GuiRegistry.sessionForInventory(event.inventory) ?: return@listen
                    session.gui.closeHandler?.invoke(session)
                    GuiRegistry.unregister(player)
                    @OptIn(studio.sculk.annotation.SculkInternal::class)
                    session.markClosed()
                }

            extraHandles +=
                events.listen<InventoryDragEvent> { event ->
                    val session = GuiRegistry.sessionForInventory(event.view.topInventory) ?: return@listen
                    val topSize = event.view.topInventory.size
                    val topSlots = event.rawSlots.filter { it < topSize }
                    // Allow drags only when every affected top slot is an interactive input slot.
                    if (topSlots.isNotEmpty() && topSlots.any { session.gui.items[it]?.interactive != true }) {
                        event.isCancelled = true
                    }
                }

            extraHandles +=
                events.listen<PlayerQuitEvent> { event ->
                    GuiRegistry.unregister(event.player)
                }
        }

        return SculkPlatform(
            plugin,
            scheduler,
            scope,
            events,
            tasks,
            commands,
            sculkConfig,
            sculkData,
            sculkText,
            sculkIntegrations,
            sculkPackets,
            extraHandles,
        )
    }
}
