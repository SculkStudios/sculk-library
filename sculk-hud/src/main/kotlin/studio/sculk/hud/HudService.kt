package studio.sculk.hud

import net.kyori.adventure.bossbar.BossBar
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.scheduler.SculkScheduler
import studio.sculk.text.SculkMessages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything drawn on a player's screen that is not chat.
 *
 * ### One task, not one per element
 *
 * The whole HUD — action bar, sidebar, tab list, boss bars, for every player — is driven by a
 * single repeating task. The obvious alternative, a task per element per player, is thousands of
 * callbacks a second at a few hundred players, and worse than the cost: the tasks drift, so the
 * sidebar and the action bar end up disagreeing about the same number in the same frame.
 *
 * Refreshing four times a second is fast enough that no player perceives lag in a counter, and
 * slow enough to be nearly free.
 *
 * ```kotlin
 * hud.start()
 * hud.sidebar(player, "<value>My Server</value>") { listOf("<center>Balance", "<coins>") }
 * hud.actionBar(player, "<danger>Your claim expires soon", ActionBarPriority.ALERT)
 * ```
 */
@SculkStable
public class HudService
@SculkInternal
constructor(
    private val scheduler: SculkScheduler,
    private val messages: SculkMessages,
    public val placeholders: Placeholders = Placeholders(),
    private val refreshTicks: Long = 5,
    private val onlinePlayers: () -> Collection<Player> = { org.bukkit.Bukkit.getOnlinePlayers() },
) : SculkHandle {
    private val actionBars = ActionBarState()
    private val sidebars = ConcurrentHashMap<UUID, Entry>()
    private val tabLists = ConcurrentHashMap<UUID, Pair<String, String>>()
    private val bossBars = ConcurrentHashMap<UUID, BossBar>()

    private var task: SculkHandle? = null
    private var tick = 0L

    /** Starts the driver. Idempotent. */
    @SculkStable
    public fun start() {
        if (task != null) return
        task = scheduler.runSyncRepeating(refreshTicks, refreshTicks, ::refresh)
    }

    /** Shows [template] on [player]'s action bar for [durationTicks]. */
    @SculkStable
    public fun actionBar(
        player: Player,
        template: String,
        priority: ActionBarPriority = ActionBarPriority.FEEDBACK,
        durationTicks: Long = 40,
        vararg values: Pair<String, String>,
    ) {
        actionBars.show(player.uniqueId, HudRow(template, values.toList()), priority, durationTicks, tick)
    }

    /** Gives [player] a sidebar whose rows are recomputed on each refresh. */
    @SculkStable
    public fun sidebar(player: Player, title: String, rows: (Player) -> List<HudRow>) {
        sidebars.compute(player.uniqueId) { _, existing ->
            existing?.sidebar?.close()
            Entry(Sidebar(player, messages, title).also { it.show() }, rows)
        }
    }

    @SculkStable
    public fun clearSidebar(player: Player) {
        sidebars.remove(player.uniqueId)?.sidebar?.close()
    }

    @SculkStable
    public fun tabList(player: Player, header: String, footer: String) {
        tabLists[player.uniqueId] = header to footer
    }

    @SculkStable
    public fun bossBar(player: Player, bar: BossBar) {
        bossBars.put(player.uniqueId, bar)?.let { player.hideBossBar(it) }
        player.showBossBar(bar)
    }

    @SculkStable
    public fun clearBossBar(player: Player) {
        bossBars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
    }

    /**
     * Drops everything held for [player].
     *
     * Call on quit. Without it the sidebar, the tab-list entry and the action-bar messages for
     * every player who has ever joined are held for the server's uptime.
     */
    @SculkStable
    public fun forget(player: Player) {
        sidebars.remove(player.uniqueId)?.sidebar?.close()
        tabLists.remove(player.uniqueId)
        bossBars.remove(player.uniqueId)
        actionBars.clear(player.uniqueId)
    }

    /** How many tasks drive the HUD. Always one, whatever the player count — asserted in tests. */
    @SculkInternal
    public val taskCount: Int get() = if (task == null) 0 else 1

    override fun close() {
        task?.close()
        task = null
        sidebars.values.forEach { it.sidebar.close() }
        sidebars.clear()
        tabLists.clear()
        bossBars.clear()
    }

    private fun refresh() {
        tick += refreshTicks
        for (player in onlinePlayers()) {
            val id = player.uniqueId

            sidebars[id]?.let { entry ->
                // A throwing row supplier must not take the whole HUD frame down with it; every
                // other player and every other element still gets drawn.
                val rows = runCatching { entry.rows(player) }.getOrNull() ?: return@let
                entry.sidebar.lines(rows.map { withPlaceholders(player, it) })
            }

            actionBars.current(id, tick)?.let { row ->
                player.sendActionBar(messages.render(row.template, *row.values.toTypedArray()))
            }

            tabLists[id]?.let { (header, footer) ->
                player.sendPlayerListHeaderAndFooter(messages.render(header), messages.render(footer))
            }
        }
    }

    /**
     * Attaches this player's placeholder values to a row.
     *
     * The values are carried alongside the template rather than substituted into it, so they reach
     * the renderer as unparsed placeholders. Substituting first is what let a player-influenced
     * value be parsed as markup, and a sidebar is a particularly good place for that to happen.
     */
    private fun withPlaceholders(player: Player, row: HudRow): HudRow {
        val resolved = placeholders.resolve(player, row.template)
        if (resolved.isEmpty()) return row
        return row.copy(values = row.values + resolved.toList())
    }

    private class Entry(val sidebar: Sidebar, val rows: (Player) -> List<HudRow>)
}
