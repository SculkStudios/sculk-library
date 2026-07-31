package studio.sculk.hud

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.text.MinecraftFont
import studio.sculk.text.SculkMessages

/** The pixel width a sidebar has before the client starts clipping. */
private const val SIDEBAR_WIDTH_PIXELS = 180

/**
 * One player's sidebar.
 *
 * Flicker-free: the objective is created once, each row keeps a stable invisible entry, and the
 * visible text lives in that row's team prefix, so an update is one prefix change with nothing
 * removed. Rows are only rewritten when their [sidebarSignature] changed.
 *
 * See [docs.sculk.studio/hud/sidebar](https://docs.sculk.studio/hud/sidebar/).
 */
@SculkStable
public class Sidebar
@SculkInternal
constructor(private val player: Player, private val messages: SculkMessages, title: String) :
    SculkHandle {
    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager().newScoreboard
    private val objective: Objective = scoreboard.registerNewObjective("sculk", Criteria.DUMMY, messages.render(title))

    private var rendered: List<String> = emptyList()
    private var widest: Int = 0
    private var shown = false

    init {
        objective.displaySlot = DisplaySlot.SIDEBAR
        // Kills the red numbers vanilla draws down the right-hand side. Without this every sidebar
        // carries a column of scores nobody wants and no styling can hide. Wrapped because the API
        // is newer than the oldest Paper build Sculk still compiles against; a server without it
        // gets the numbers rather than a failure to start.
        runCatching { objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank()) }
    }

    /** Shows this sidebar to its player. */
    @SculkStable
    public fun show() {
        if (shown) return
        player.scoreboard = scoreboard
        shown = true
    }

    /** Restores the player's previous scoreboard. */
    @SculkStable
    public fun hide() {
        if (!shown) return
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        shown = false
    }

    @SculkStable
    public fun title(value: String) {
        objective.displayName(messages.render(value))
    }

    /**
     * Replaces the rows.
     *
     * A row starting with `<center>` is padded to sit centred, measured with [MinecraftFont] --
     * padding by character count is visibly off in a proportional font.
     *
     * Rows beyond [MAX_SIDEBAR_LINES] are dropped here rather than silently by the client.
     */
    @SculkStable
    public fun lines(rows: List<HudRow>) {
        val capped = rows.take(MAX_SIDEBAR_LINES)
        // Values go in as unparsed placeholders, never substituted into the template first.
        val components = capped.map { messages.render(it.body, *it.values.toTypedArray()) }
        val newWidest = components.maxOfOrNull { MinecraftFont.width(it) } ?: 0
        val widestChanged = newWidest != widest
        widest = newWidest
        val moved = rendered.size != capped.size

        for ((index, row) in capped.withIndex()) {
            val changed = rendered.getOrNull(index) != row.signature
            if (!needsRedraw(changed, moved, row.centred, widestChanged)) continue
            writeRow(index, components[index], row.centred, capped.size)
        }

        // Rows that no longer exist have their entries removed; leaving them shows stale text.
        for (index in capped.size until rendered.size) {
            scoreboard.getEntryTeam(rowEntry(index))?.unregister()
            scoreboard.resetScores(rowEntry(index))
        }
        rendered = capped.map { it.signature }
    }

    override fun close() {
        hide()
        runCatching { objective.unregister() }
    }

    private fun writeRow(index: Int, component: Component, centred: Boolean, total: Int) {
        val entry = rowEntry(index)
        val team = scoreboard.getTeam("row$index") ?: scoreboard.registerNewTeam("row$index")
        if (!team.hasEntry(entry)) team.addEntry(entry)

        val padding = if (centred) MinecraftFont.centre(component, SIDEBAR_WIDTH_PIXELS) else ""
        team.prefix(if (padding.isEmpty()) component else Component.text(padding).append(component))

        val score = objective.getScore(entry)
        val wanted = scoreFor(index, total)
        // A setScore is a packet even when the value is identical, so only write when it moved.
        if (!score.isScoreSet || score.score != wanted) score.score = wanted
    }
}
