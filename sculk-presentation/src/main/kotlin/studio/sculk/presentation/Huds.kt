package studio.sculk.presentation

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.title.Title
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
import java.util.Collections
import java.util.IdentityHashMap
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
    internal val names = NameBoards(plugin)

    init {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        placeholders?.onChange(::route)
        scope.onClose {
            huds.values.toList().forEach { it.close() }
            HandlerList.unregisterAll(listener)
        }
    }

    /** Whether this server can draw a sidebar, nametags and below-name lines: Paper and Canvas, not Folia (ADR-0023). */
    public val sidebarAvailable: Boolean get() = SCOREBOARDS

    /**
     * Shows [layout] to [player], replacing any HUD of this `Huds` they had, from any thread. A
     * layout with a sidebar, nametag or below-name line on a server without scoreboards returns
     * [SculkFailure.UnsupportedCapability] and shows nothing.
     */
    public fun show(player: Player, layout: HudLayout): SculkResult<PlayerHud> {
        val needsBoard = layout.surfaces.firstOrNull { it is Surface.Sidebar || it is Surface.Nametag || it is Surface.BelowName }
        if (!SCOREBOARDS && needsBoard != null) {
            return SculkFailure.UnsupportedCapability(
                needsBoard::class.simpleName ?: "Scoreboard",
                "Folia has no scoreboards; sculk-presentation-packets draws them with packets",
            )
        }
        val hud = PlayerHud(this, plugin, player, layout, text, placeholders)
        huds.put(player.uniqueId, hud)?.close()
        hud.start()
        return SculkResult.success(hud)
    }

    /**
     * Shows [title] and [subtitle], templates rendered with this `Huds`'s placeholders, to [player]
     * once, from any thread; it shows on the player's next tick at the latest and the client fades
     * it after [times]. A title belongs to no HUD and needs none.
     */
    public fun title(player: Player, title: String, subtitle: String = "", times: Title.Times = Title.DEFAULT_TIMES) {
        val show = {
            val resolver = placeholders?.resolver(player) ?: TagResolver.empty()
            player.showTitle(Title.title(text.render(title, resolver), text.render(subtitle, resolver), times))
        }
        if (Bukkit.isOwnedByCurrentRegion(player)) show() else player.scheduler.run(plugin, { show() }, null)
    }

    /** Clears [player]'s title, from any thread. */
    public fun clearTitle(player: Player) {
        if (Bukkit.isOwnedByCurrentRegion(player)) player.clearTitle() else player.scheduler.run(plugin, { player.clearTitle() }, null)
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
}

// Decided by class, because probing with a write would itself be refused off the main thread on Paper.
// Folia and Canvas are regionized; Folia throws on every scoreboard write, and Canvas restores them but
// checks that each runs on the global tick.
private val REGIONIZED: Boolean by lazy { exists("io.papermc.paper.threadedregions.RegionizedServer") }
private val SCOREBOARDS: Boolean by lazy { !REGIONIZED || exists("io.canvasmc.canvas.region.RegionTickData") }

private fun exists(name: String): Boolean = runCatching { Class.forName(name, false, Huds::class.java.classLoader) }.isSuccess

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
    private val sink = PaperSink(plugin, player, huds.names)
    private val engine = HudEngine(layout, player, ::render, sink)
    private val start = System.nanoTime()
    private var pending: Long? = null

    // Every tag a render of this HUD has asked for, so a change to any other costs no task. Written on
    // the player's thread before the value is read, so a change racing that read is still delivered.
    @Volatile private var watched: Set<String> = emptySet()

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
        if (!isOpen || tag !in watched) return
        onPlayer { if (isOpen && engine.mark(tag)) wakeAt(now()) }
    }

    // The player has gone: nothing can be sent and the server drops their bars, but their board stays
    // registered for nametag copies and their name's teams and scores live on shared boards.
    internal fun left() {
        isOpen = false
        huds.ended(this)
        huds.names.forget(player.name)
        sink.release()
    }

    private fun render(template: String): Rendered {
        val used = HashSet<String>()
        val resolver = placeholders?.resolver(player)?.let { Recording(it, used, ::watch) } ?: TagResolver.empty()
        return Rendered(text.render(template, resolver), used)
    }

    private fun watch(tag: String) {
        if (tag !in watched) watched = watched + tag
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
    private class Recording(private val delegate: TagResolver, private val used: MutableSet<String>, private val watch: (String) -> Unit) :
        TagResolver {
        override fun resolve(name: String, arguments: ArgumentQueue, context: Context): Tag? {
            if (delegate.has(name)) watch(name)
            return delegate.resolve(name, arguments, context).also { if (it != null) used += name }
        }

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
private class PaperSink(private val plugin: Plugin, private val player: Player, private val names: NameBoards) : HudSink {
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

    override fun nametag(shown: Shown.Nametag?): Boolean = names.nametag(player.name, shown)

    override fun belowName(shown: Shown.BelowName?): Boolean = names.belowName(player.name, shown?.text)

    // Canvas requires creating the board, like every scoreboard write, on the global tick.
    private fun create(main: Scoreboard) {
        creating = true
        previous = main
        writes {
            // Cleared even when a write throws, or this HUD would never try to make a sidebar again.
            try {
                val created = Bukkit.getScoreboardManager().newScoreboard
                val made = created.registerNewObjective(OBJECTIVE, Criteria.DUMMY, latest?.title ?: Component.empty())
                made.numberFormat(NumberFormat.blank())
                made.setDisplaySlot(DisplaySlot.SIDEBAR)
                latest?.let { fill(made, it) }
                names.adopt(created)
                objective = made
                board = created
                onPlayer { if (board === created && player.scoreboard === main) player.scoreboard = created }
            } finally {
                creating = false
            }
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
        names.drop(ours)
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
    }
}

/**
 * The nametags and below-name lines of every HUD of one `Huds`: one team and one score per player,
 * on the main board and copied onto each board Sculk gave a player for a sidebar, so the cost grows
 * with the player count, not its square. Text is the target's, the same for every viewer.
 */
internal class NameBoards(private val plugin: Plugin) {
    // Touched only inside writes: the main thread on Paper, the global tick on Canvas.
    private val nametags = HashMap<String, Shown.Nametag>()
    private val belowNames = HashMap<String, Component>()
    private val boards: MutableSet<Scoreboard> = Collections.newSetFromMap(IdentityHashMap())

    /** Shows or removes [entry]'s nametag; false while another plugin has them in a team on the main board. */
    fun nametag(entry: String, shown: Shown.Nametag?): Boolean {
        if (shown != null && foreignTeam(entry)) return false
        writes {
            when {
                shown == null -> if (nametags.remove(entry) != null) everyBoard { it.getTeam(TEAM + entry)?.unregister() }

                // Re-checked here: another plugin may have taken the player between the check and this write.
                foreignTeam(entry) -> {}

                else -> {
                    nametags[entry] = shown
                    everyBoard { writeTeam(it, entry, shown) }
                }
            }
        }
        return true
    }

    /** Shows or removes [entry]'s below-name line; false while another plugin's objective holds that slot. */
    fun belowName(entry: String, text: Component?): Boolean {
        if (text != null && foreignBelowName()) return false
        writes {
            when {
                text == null -> if (belowNames.remove(entry) != null) everyBoard { removeScore(it, entry) }

                foreignBelowName() -> {}

                else -> {
                    belowNames[entry] = text
                    everyBoard { writeScore(it, entry, text) }
                }
            }
        }
        return true
    }

    fun forget(entry: String) {
        nametag(entry, null)
        belowName(entry, null)
    }

    /** Copies everything onto [board], a board Sculk just made; called inside writes. */
    fun adopt(board: Scoreboard) {
        boards += board
        nametags.forEach { (entry, shown) -> writeTeam(board, entry, shown) }
        belowNames.forEach { (entry, text) -> writeScore(board, entry, text) }
    }

    fun drop(board: Scoreboard) {
        writes { boards -= board }
    }

    private fun everyBoard(action: (Scoreboard) -> Unit) {
        action(Bukkit.getScoreboardManager().mainScoreboard)
        boards.forEach(action)
    }

    private fun foreignTeam(entry: String): Boolean =
        Bukkit.getScoreboardManager().mainScoreboard.getEntryTeam(entry)?.let { it.name != TEAM + entry } ?: false

    private fun foreignBelowName(): Boolean =
        Bukkit.getScoreboardManager().mainScoreboard.getObjective(DisplaySlot.BELOW_NAME)?.let { it.name != OBJECTIVE } ?: false

    private fun writeTeam(board: Scoreboard, entry: String, shown: Shown.Nametag) {
        val team = board.getTeam(TEAM + entry) ?: board.registerNewTeam(TEAM + entry)
        if (team.prefix() != shown.prefix) team.prefix(shown.prefix)
        if (team.suffix() != shown.suffix) team.suffix(shown.suffix)
        if (!team.hasEntry(entry)) team.addEntry(entry)
    }

    // The client draws a below-name score as its number format followed by the objective's name, so
    // a fixed format per player and an empty name show exactly the rendered text.
    private fun writeScore(board: Scoreboard, entry: String, text: Component) {
        val objective = board.getObjective(OBJECTIVE)
            ?: board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty()).also { it.setDisplaySlot(DisplaySlot.BELOW_NAME) }
        val score = objective.getScore(entry)
        val format = NumberFormat.fixed(text)
        if (!score.isScoreSet) score.score = 0
        if (score.numberFormat() != format) score.numberFormat(format)
    }

    private fun removeScore(board: Scoreboard, entry: String) {
        val objective = board.getObjective(OBJECTIVE) ?: return
        if (belowNames.isEmpty()) objective.unregister() else objective.getScore(entry).resetScore()
    }

    private fun writes(action: () -> Unit) {
        if (REGIONIZED) Bukkit.getGlobalRegionScheduler().run(plugin) { action() } else action()
    }

    private companion object {
        const val TEAM = "sculk_name_"
        const val OBJECTIVE = "sculk_below"
    }
}
