package studio.sculk.presentation

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import studio.sculk.ResourceScope
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.placeholder.PlaceholderChange
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Shows [HudLayout]s, one per player, and redraws each surface only when a placeholder it used
 * changed, its interval came round or its condition flipped (ADR-0030). Every HUD ends when its
 * player leaves or [scope] closes.
 *
 * Thread-safe. A HUD runs on its player's thread: the main thread on Paper, the player's region on
 * Folia and Canvas.
 */
@SculkStable
public class Huds(
    private val plugin: Plugin,
    scope: ResourceScope,
    private val placeholders: PlaceholderRegistry? = null,
    private val text: Text = Text(),
) {
    private val huds = ConcurrentHashMap<UUID, PlayerHud>()
    private val listener = QuitListener()

    init {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        placeholders?.onChange(::route)
        scope.onClose {
            huds.values.toList().forEach { it.close() }
            HandlerList.unregisterAll(listener)
        }
    }

    /** Whether this server can draw a sidebar: Paper and Canvas, not Folia (ADR-0023). */
    public val sidebarAvailable: Boolean get() = SCOREBOARDS

    /**
     * Shows [layout] to [player], replacing any HUD of this `Huds` they had, from any thread. A
     * layout with a sidebar on a server without scoreboards returns
     * [SculkFailure.UnsupportedCapability] and shows nothing.
     */
    public fun show(player: Player, layout: HudLayout): SculkResult<PlayerHud> {
        if (!SCOREBOARDS && layout.surfaces.any { it is Surface.Sidebar }) {
            return SculkFailure.UnsupportedCapability(
                "Sidebar",
                "Folia has no scoreboards; sculk-presentation-packets draws them with packets",
            )
        }
        val hud = PlayerHud(this, plugin, player, layout, text, placeholders)
        huds.put(player.uniqueId, hud)?.close()
        hud.start()
        return SculkResult.success(hud)
    }

    /** The HUD [player] has from this `Huds`, if any. */
    public fun hud(player: Player): PlayerHud? = huds[player.uniqueId]

    internal fun ended(hud: PlayerHud) {
        huds.remove(hud.player.uniqueId, hud)
    }

    private fun route(change: PlaceholderChange) {
        val tag = change.key.tag
        when (change) {
            is PlaceholderChange.Everyone -> huds.values.forEach { it.signal(tag) }
            is PlaceholderChange.Audience -> huds[change.uniqueId]?.signal(tag)
            is PlaceholderChange.Relation -> huds[change.viewer]?.signal(tag)
        }
    }

    private inner class QuitListener : Listener {
        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            huds[event.player.uniqueId]?.left()
        }
    }

    private companion object {
        // Folia throws on every scoreboard write; Canvas restores them. Decided by class, because
        // probing with a write would itself be refused off the main thread on Paper.
        val SCOREBOARDS: Boolean by lazy { !exists(REGIONIZED_SERVER) || exists(CANVAS_CLASS) }

        const val REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer"
        const val CANVAS_CLASS = "io.canvasmc.canvas.region.RegionTickData"

        fun exists(name: String): Boolean = runCatching { Class.forName(name, false, Huds::class.java.classLoader) }.isSuccess
    }
}

/** One player's HUD. [refresh] redraws everything; [close] removes it. */
@SculkStable
public class PlayerHud internal constructor(
    private val huds: Huds,
    private val plugin: Plugin,
    public val player: Player,
    layout: HudLayout,
    private val text: Text,
    private val placeholders: PlaceholderRegistry?,
) {
    private val sink = PaperSink(plugin, player)
    private val engine = HudEngine(layout, player, ::render, sink)
    private val start = System.nanoTime()
    private var pending: Long? = null

    @Volatile public var isOpen: Boolean = true
        private set

    /** Redraws every surface on the player's next tick, as for a `Manual` update. */
    public fun refresh() {
        onPlayer {
            engine.markAll()
            wakeAt(now())
        }
    }

    /** Removes everything the HUD shows and ends it. */
    public fun close() {
        if (!isOpen) return
        isOpen = false
        huds.ended(this)
        onPlayer {
            engine.close()
            sink.release()
        }
    }

    internal fun start() {
        onPlayer { wakeAt(now()) }
    }

    internal fun signal(tag: String) {
        onPlayer { if (isOpen && engine.mark(tag)) wakeAt(now()) }
    }

    // The player has gone: nothing can be sent, and the server drops their board and bars.
    internal fun left() {
        isOpen = false
        huds.ended(this)
    }

    private fun render(template: String): Rendered {
        val used = HashSet<String>()
        val resolver = placeholders?.resolver(player)?.let { Recording(it, used) } ?: TagResolver.empty()
        return Rendered(text.render(template, resolver), used)
    }

    /** At most one wake pending; an earlier request schedules an earlier one. Player thread only. */
    private fun wakeAt(tick: Long) {
        if (!isOpen) return
        pending?.let { if (it <= tick) return }
        pending = tick
        val delay = (tick - now()).coerceAtLeast(1)
        player.scheduler.runDelayed(plugin, { _ -> run(tick) }, null, delay)
    }

    private fun run(tick: Long) {
        if (pending == tick) pending = null
        if (!isOpen) return
        engine.wake(now())?.let(::wakeAt)
    }

    private fun now(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / MILLIS_PER_TICK

    private fun onPlayer(action: () -> Unit) {
        if (Bukkit.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    /** Records which tags a render resolved, so a change to one marks this surface. */
    private class Recording(private val delegate: TagResolver, private val used: MutableSet<String>) : TagResolver {
        override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? =
            delegate.resolve(name, arguments, context).also { if (it != null) used += name }

        override fun has(name: String): Boolean = delegate.has(name)
    }

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}

/**
 * Draws on a real player. Scoreboard writes go to the global region on Canvas, which requires them
 * there, and run inline on Paper; assigning the player's board stays on the player's thread.
 */
private class PaperSink(private val plugin: Plugin, private val player: Player) : HudSink {
    private val bars = HashMap<String, BossBar>()

    // Written on the global tick on Canvas and read on the player's thread.
    @Volatile private var board: Scoreboard? = null

    @Volatile private var objective: Objective? = null

    @Volatile private var previous: Scoreboard? = null

    @Volatile private var creating = false

    @Volatile private var latest: Shown.Sidebar? = null

    override fun actionBar(text: Component) {
        player.sendActionBar(text)
    }

    override fun bar(id: String, shown: Shown.Bar?) {
        val existing = bars[id]
        if (shown == null) {
            existing?.let { player.hideBossBar(it) }
            bars.remove(id)
            return
        }
        if (existing == null) {
            val bar = BossBar.bossBar(shown.title, shown.progress, shown.color, shown.overlay)
            bars[id] = bar
            player.showBossBar(bar)
            return
        }
        if (existing.name() != shown.title) existing.name(shown.title)
        if (existing.progress() != shown.progress) existing.progress(shown.progress)
        if (existing.color() != shown.color) existing.color(shown.color)
        if (existing.overlay() != shown.overlay) existing.overlay(shown.overlay)
    }

    override fun tabList(shown: Shown.TabList?) {
        player.sendPlayerListHeaderAndFooter(shown?.header ?: Component.empty(), shown?.footer ?: Component.empty())
    }

    override fun sidebar(shown: Shown.Sidebar?): Boolean {
        if (shown == null) {
            release()
            return true
        }
        latest = shown
        val main = Bukkit.getScoreboardManager().mainScoreboard
        val current = player.scoreboard
        val ours = board
        // The lease: take the player's board only from the main board, never from another plugin's.
        if (current !== main && current !== ours) {
            board = null
            objective = null
            return false
        }
        if (ours == null) {
            if (!creating) create(main)
            return true
        }
        // Created on the global tick but not yet handed to the player, or reset to the main board since.
        if (current === main) onPlayer { if (board === ours && player.scoreboard === main) player.scoreboard = ours }
        writes { objective?.let { made -> latest?.let { fill(made, it) } } }
        return true
    }

    // Canvas requires creating the board, like every scoreboard write, on the global tick.
    private fun create(main: Scoreboard) {
        creating = true
        previous = main
        writes {
            val created = Bukkit.getScoreboardManager().newScoreboard
            val made = created.registerNewObjective(OBJECTIVE, Criteria.DUMMY, latest?.title ?: Component.empty())
            made.numberFormat(NumberFormat.blank())
            made.setDisplaySlot(DisplaySlot.SIDEBAR)
            latest?.let { fill(made, it) }
            objective = made
            board = created
            creating = false
            onPlayer { if (board === created && player.scoreboard === main) player.scoreboard = created }
        }
    }

    private fun fill(objective: Objective, shown: Shown.Sidebar) {
        if (objective.displayName() != shown.title) objective.displayName(shown.title)
        for (index in 0 until Surface.Sidebar.MAX_LINES) {
            val score = objective.getScore("$ENTRY$index")
            val line = shown.lines.getOrNull(index)
            if (line == null) {
                if (score.isScoreSet) score.resetScore()
                continue
            }
            if (!score.isScoreSet || score.score != shown.lines.size - index) score.score = shown.lines.size - index
            if (score.customName() != line) score.customName(line)
        }
    }

    /** Gives back the board the player had, if Sculk's is still the one they see. */
    fun release() {
        latest = null
        val ours = board ?: return
        val back = previous ?: Bukkit.getScoreboardManager().mainScoreboard
        board = null
        objective = null
        if (player.isOnline && player.scoreboard === ours) player.scoreboard = back
    }

    private fun writes(action: () -> Unit) {
        if (REGIONIZED) Bukkit.getGlobalRegionScheduler().run(plugin) { action() } else action()
    }

    private fun onPlayer(action: () -> Unit) {
        if (Bukkit.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    private companion object {
        const val OBJECTIVE = "sculk_hud"
        const val ENTRY = "sculk_line_"

        // Canvas restores scoreboards but checks that writes run on the global tick; Paper has one
        // thread. Folia is regionized too, but never gets here: it has no scoreboards to write.
        val REGIONIZED: Boolean by lazy {
            runCatching { Class.forName("io.papermc.paper.threadedregions.RegionizedServer") }.isSuccess
        }
    }
}
