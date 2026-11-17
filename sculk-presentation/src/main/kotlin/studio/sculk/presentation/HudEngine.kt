package studio.sculk.presentation

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/** What a surface rendered to; equal values mean nothing needs sending. */
internal sealed interface Shown {
    data class ActionBar(val text: Component) : Shown

    data class Bar(val title: Component, val progress: Float, val color: BossBar.Color, val overlay: BossBar.Overlay) : Shown

    data class TabList(val header: Component, val footer: Component) : Shown

    data class Sidebar(val title: Component, val lines: List<Component>) : Shown

    data class Nametag(val prefix: Component, val suffix: Component) : Shown

    data class BelowName(val text: Component) : Shown
}

/** Where a HUD draws; the Paper implementation talks to the player, tests record. */
internal interface HudSink {
    fun actionBar(text: Component)

    fun bar(id: String, shown: Shown.Bar?)

    fun tabList(shown: Shown.TabList?)

    /** Shows or, with null, removes the sidebar; false when another plugin's board holds the player and Sculk yields. */
    fun sidebar(shown: Shown.Sidebar?): Boolean

    /** Shows or removes the player's nametag; false while another plugin has them in a team. */
    fun nametag(shown: Shown.Nametag?): Boolean

    /** Shows or removes the player's below-name line; false while another plugin holds that slot. */
    fun belowName(shown: Shown.BelowName?): Boolean
}

/** A template rendered, with the placeholder tags it resolved. */
internal class Rendered(val component: Component, val tags: Set<String>)

/**
 * One player's HUD as a render graph (ADR-0030): each surface remembers what it last sent and the
 * placeholder tags it used, and [wake] renders only what is dirty, due or newly shown or hidden.
 * Not thread-safe: the driver calls it on the player's thread only.
 */
internal class HudEngine(
    layout: HudLayout,
    private val player: Player,
    private val render: (String) -> Rendered,
    private val sink: HudSink,
) {
    private inner class State(val surface: Surface) {
        var sent: Shown? = null
        var tags: Set<String> = emptySet()
        var dirty = true
        var nextDue = 0L
        var lastSend = Long.MIN_VALUE / 2
        var shownBefore: Boolean? = null
    }

    private val states = layout.surfaces.map { State(it) }

    // The surfaces that redraw on a change to each tag, kept as tags are recorded; Manual surfaces never join.
    private val byTag = HashMap<String, MutableList<State>>()

    /** Marks the surfaces that used [tag], except `Manual` ones; true when any did, so the driver should wake. */
    fun mark(tag: String): Boolean {
        val hit = byTag[tag] ?: return false
        hit.forEach { it.dirty = true }
        return true
    }

    fun markAll() {
        states.forEach { it.dirty = true }
    }

    /** Renders what needs it at tick [now]; returns the tick of the next wake that time alone requires, or null. */
    fun wake(now: Long): Long? {
        var next: Long? = null
        fun due(at: Long) {
            next = next?.let { minOf(it, at) } ?: at
        }
        for (state in states) {
            val surface = state.surface
            val visible = surface.showWhen?.invoke(player) ?: true
            val flipped = state.shownBefore != visible
            val interval = (surface.update as? Update.Every)?.ticks
            val timed = interval != null && now >= state.nextDue
            val fading = surface is Surface.ActionBar && visible && now - state.lastSend >= ACTION_BAR_RESEND
            if (state.dirty || timed || flipped || fading || (state.sent == null && visible)) {
                // A Manual action bar is resent as it was, so the fade never re-renders its placeholders.
                val unchanged = fading && !state.dirty && !flipped && state.sent != null && surface.update == Update.Manual
                when {
                    !visible -> hide(state)
                    unchanged -> resend(state, now)
                    else -> draw(state, now, force = fading)
                }
                state.shownBefore = visible
                if (interval != null && timed) state.nextDue = now + interval
            }
            interval?.let { due(state.nextDue) }
            if (surface.showWhen != null) due(now + CONDITION_CHECK)
            if (surface is Surface.ActionBar && visible) due(state.lastSend + ACTION_BAR_RESEND)
            if (state.dirty) due(now + YIELD_RETRY)
        }
        return next
    }

    /** Removes everything this HUD shows. */
    fun close() {
        // The sidebar goes last: releasing its board shows the main board, which must no longer
        // carry this player's nametag and below-name line by then.
        states.sortedBy { it.surface is Surface.Sidebar }.forEach { hide(it) }
    }

    private fun draw(state: State, now: Long, force: Boolean) {
        val tags = HashSet<String>()
        fun text(template: String): Component = render(template).also { tags += it.tags }.component
        val shown: Shown = when (val surface = state.surface) {
            is Surface.ActionBar -> Shown.ActionBar(text(surface.text))

            is Surface.Bar -> Shown.Bar(
                text(surface.title),
                surface.progress?.invoke(player)?.coerceIn(0f, 1f) ?: 1f,
                surface.color,
                surface.overlay,
            )

            is Surface.TabList -> Shown.TabList(text(surface.header), text(surface.footer))

            is Surface.Sidebar -> Shown.Sidebar(text(surface.title), surface.lines.map(::text))

            is Surface.Nametag -> Shown.Nametag(text(surface.prefix), text(surface.suffix))

            is Surface.BelowName -> Shown.BelowName(text(surface.text))
        }
        if (tags != state.tags) index(state, tags)
        state.dirty = false
        if (shown == state.sent && !force) return
        when (shown) {
            is Shown.ActionBar -> sink.actionBar(shown.text)

            is Shown.Bar -> sink.bar((state.surface as Surface.Bar).id, shown)

            is Shown.TabList -> sink.tabList(shown)

            // Another plugin holds the board, team or slot: stay dirty and try again later.
            is Shown.Sidebar -> if (!sink.sidebar(shown)) return yielded(state)

            is Shown.Nametag -> if (!sink.nametag(shown)) return yielded(state)

            is Shown.BelowName -> if (!sink.belowName(shown)) return yielded(state)
        }
        state.sent = shown
        state.lastSend = now
    }

    private fun index(state: State, tags: Set<String>) {
        if (state.surface.update != Update.Manual) {
            for (tag in state.tags - tags) {
                val users = byTag[tag] ?: continue
                users.remove(state)
                if (users.isEmpty()) byTag.remove(tag)
            }
            for (tag in tags - state.tags) byTag.getOrPut(tag) { ArrayList<State>(1) }.add(state)
        }
        state.tags = tags
    }

    private fun resend(state: State, now: Long) {
        val sent = state.sent as? Shown.ActionBar ?: return
        sink.actionBar(sent.text)
        state.lastSend = now
    }

    private fun hide(state: State) {
        if (state.sent == null) return
        when (val surface = state.surface) {
            is Surface.ActionBar -> sink.actionBar(Component.empty())
            is Surface.Bar -> sink.bar(surface.id, null)
            is Surface.TabList -> sink.tabList(null)
            is Surface.Sidebar -> sink.sidebar(null)
            is Surface.Nametag -> sink.nametag(null)
            is Surface.BelowName -> sink.belowName(null)
        }
        state.sent = null
    }

    private fun yielded(state: State) {
        state.dirty = true
    }

    private companion object {
        const val ACTION_BAR_RESEND = 40L
        const val CONDITION_CHECK = 20L
        const val YIELD_RETRY = 20L
    }
}
