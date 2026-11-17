package studio.sculk.presentation

import net.kyori.adventure.bossbar.BossBar
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkStable

/** When a surface renders again. */
@SculkStable
public sealed class Update {
    /** When a placeholder the surface used signals a change (ADR-0026); never on a timer. */
    @SculkStable
    public data object OnChange : Update()

    /** Every [ticks] ticks, and on placeholder changes too. */
    @SculkStable
    public class Every(public val ticks: Long) : Update() {
        init {
            require(ticks > 0) { "an update interval is at least one tick" }
        }

        override fun equals(other: Any?): Boolean = other is Every && other.ticks == ticks

        override fun hashCode(): Int = ticks.hashCode()

        override fun toString(): String = "Every($ticks ticks)"
    }

    /** Only when the plugin calls `PlayerHud.refresh`. */
    @SculkStable
    public data object Manual : Update()
}

/**
 * One part of a HUD. Templates are MiniMessage rendered with the `Huds` theme and placeholders;
 * [showWhen], if set, is checked on the player's thread at least once a second and hides the
 * surface while it is false.
 */
@SculkStable
public sealed class Surface(public val update: Update, internal val showWhen: ((Player) -> Boolean)?) {
    /** The line above the hotbar. Resent every two seconds while shown, because the client fades it. */
    @SculkStable
    public class ActionBar(public val text: String, update: Update = Update.OnChange, showWhen: ((Player) -> Boolean)? = null) :
        Surface(update, showWhen)

    /** A boss bar; [id] tells a HUD's bars apart. [progress], from 0 to 1, is read on each render. */
    @SculkStable
    public class Bar(
        public val id: String,
        public val title: String,
        public val color: BossBar.Color = BossBar.Color.PURPLE,
        public val overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
        internal val progress: ((Player) -> Float)? = null,
        update: Update = Update.OnChange,
        showWhen: ((Player) -> Boolean)? = null,
    ) : Surface(update, showWhen)

    /** The tab list's header and footer. */
    @SculkStable
    public class TabList(
        public val header: String,
        public val footer: String,
        update: Update = Update.OnChange,
        showWhen: ((Player) -> Boolean)? = null,
    ) : Surface(update, showWhen)

    /**
     * The scoreboard sidebar: a [title] and up to fifteen [lines], top first, without score numbers.
     * It needs the player's scoreboard, which Folia does not offer (ADR-0023).
     */
    @SculkStable
    public class Sidebar(
        public val title: String,
        lines: List<String>,
        update: Update = Update.OnChange,
        showWhen: ((Player) -> Boolean)? = null,
    ) : Surface(update, showWhen) {
        public val lines: List<String> = lines.toList()

        init {
            require(lines.size <= MAX_LINES) { "a sidebar shows at most $MAX_LINES lines, not ${lines.size}" }
        }

        @SculkStable
        public companion object {
            public const val MAX_LINES: Int = 15
        }
    }

    /**
     * What every other player sees before and after this player's name, above their head and in
     * the tab list. Rendered for this player, so all viewers see the same text. Yields while another
     * plugin has this player in a team; needs scoreboards, which Folia does not offer.
     */
    @SculkStable
    public class Nametag(
        public val prefix: String = "",
        public val suffix: String = "",
        update: Update = Update.OnChange,
        showWhen: ((Player) -> Boolean)? = null,
    ) : Surface(update, showWhen)

    /**
     * The line every other player sees under this player's name. Rendered for this player; yields
     * while another plugin's objective holds the below-name slot; needs scoreboards.
     */
    @SculkStable
    public class BelowName(public val text: String, update: Update = Update.OnChange, showWhen: ((Player) -> Boolean)? = null) :
        Surface(update, showWhen)
}

/**
 * What a player's HUD shows: at most one action bar, tab list, sidebar, nametag and below-name line,
 * and any number of boss bars with distinct ids.
 *
 * ```kotlin
 * val layout = hud {
 *     actionBar("<muted>Balance: <value><economy_balance>")
 *     sidebar("<primary>Sculk Network", "Online: <server_online>", "Rank: <player_rank>")
 *     tabList(header = "<primary>Sculk Network", footer = "<muted>play.sculk.studio")
 *     bar("event", "<warning>Event ends in <event_left>", progress = { event.progress() }, update = Update.Every(20))
 * }
 * huds.show(player, layout)
 * ```
 */
@SculkStable
public class HudLayout internal constructor(surfaces: List<Surface>) {
    public val surfaces: List<Surface> = surfaces.toList()

    init {
        listOf(
            Surface.ActionBar::class,
            Surface.TabList::class,
            Surface.Sidebar::class,
            Surface.Nametag::class,
            Surface.BelowName::class,
        ).forEach { kind ->
            require(surfaces.count { kind.isInstance(it) } <= 1) { "a HUD shows one ${kind.simpleName} at most" }
        }
        val bars = surfaces.filterIsInstance<Surface.Bar>().map { it.id }
        require(bars.distinct().size == bars.size) { "boss bar ids must differ" }
    }
}

/** Builds a [HudLayout]; see [hud]. */
@SculkStable
public class HudBuilder internal constructor() {
    private val surfaces = mutableListOf<Surface>()

    public fun surface(surface: Surface) {
        surfaces += surface
    }

    public fun actionBar(text: String, update: Update = Update.OnChange, showWhen: ((Player) -> Boolean)? = null) {
        surface(Surface.ActionBar(text, update, showWhen))
    }

    public fun bar(
        id: String,
        title: String,
        color: BossBar.Color = BossBar.Color.PURPLE,
        overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
        progress: ((Player) -> Float)? = null,
        update: Update = Update.OnChange,
        showWhen: ((Player) -> Boolean)? = null,
    ) {
        surface(Surface.Bar(id, title, color, overlay, progress, update, showWhen))
    }

    public fun tabList(header: String, footer: String, update: Update = Update.OnChange, showWhen: ((Player) -> Boolean)? = null) {
        surface(Surface.TabList(header, footer, update, showWhen))
    }

    public fun sidebar(title: String, vararg lines: String, update: Update = Update.OnChange, showWhen: ((Player) -> Boolean)? = null) {
        surface(Surface.Sidebar(title, lines.toList(), update, showWhen))
    }

    public fun nametag(
        prefix: String = "",
        suffix: String = "",
        update: Update = Update.OnChange,
        showWhen: ((Player) -> Boolean)? = null,
    ) {
        surface(Surface.Nametag(prefix, suffix, update, showWhen))
    }

    public fun belowName(text: String, update: Update = Update.OnChange, showWhen: ((Player) -> Boolean)? = null) {
        surface(Surface.BelowName(text, update, showWhen))
    }

    internal fun build(): HudLayout = HudLayout(surfaces)
}

/** Declares a [HudLayout]. */
@SculkStable
public fun hud(build: HudBuilder.() -> Unit): HudLayout = HudBuilder().apply(build).build()
