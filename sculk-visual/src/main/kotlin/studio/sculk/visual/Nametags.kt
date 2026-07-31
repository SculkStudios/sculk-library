package studio.sculk.visual

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import studio.sculk.packets.TextDisplayStyle
import studio.sculk.packets.VirtualEntityService
import studio.sculk.scheduler.SculkScheduler
import studio.sculk.text.SculkMessages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/** How a nametag looks and where it sits. */
@SculkStable
public data class NametagStyle(
    public val style: TextDisplayStyle = TextDisplayStyle(shadowed = true),
    /** Whether the wearer sees their own tag. Usually not — it obscures their own view. */
    public val visibleToSelf: Boolean = false,
)

/**
 * Multi-line text above a player's head.
 *
 * ### Why it rides the player
 *
 * The display is mounted on its wearer rather than teleported to follow them. A teleport loop
 * visibly lags: the tag swims behind the player at anything above walking pace, because it only
 * moves as often as the task runs. A mounted entity is interpolated by the client, so it tracks
 * the player exactly and costs one packet at spawn instead of one per tick.
 *
 * ### Why there are two passes
 *
 * Most tags change rarely — a rank, a name. Those are re-rendered on the slow pass. A tag that
 * animates needs a much faster one, but running *every* tag at that rate is wasted work
 * proportional to the player count. Only wearers that opted into animation are visited on the fast
 * pass.
 *
 * ### The kill switch
 *
 * A template that throws would otherwise produce a stack trace per player per period — thousands
 * of lines a minute, which is a worse outage than the broken tag. The first failure sets [broken],
 * logs once, and stops the renderer until something calls [reset].
 */
@SculkStable
public class Nametags
@SculkInternal
constructor(
    private val entities: VirtualEntityService,
    private val messages: SculkMessages,
    private val scheduler: SculkScheduler,
    private val logger: Logger,
    private val style: NametagStyle = NametagStyle(),
    private val onlinePlayers: () -> Collection<Player> = { org.bukkit.Bukkit.getOnlinePlayers() },
    private val playerOf: (UUID) -> Player? = { org.bukkit.Bukkit.getPlayer(it) },
) : SculkHandle {
    private val wearers = ConcurrentHashMap<UUID, Wearer>()
    private val animated = ConcurrentHashMap.newKeySet<UUID>()
    private val handles = mutableListOf<SculkHandle>()

    /** Set when a template threw. No tag is rendered until [reset] clears it. */
    @Volatile
    public var broken: Boolean = false
        private set

    /**
     * Starts both passes.
     *
     * [fastPeriodTicks] is 1 by default because that is the ceiling anyway — metadata cannot be
     * flushed to a client more often than once a tick, so a smaller number would only burn CPU.
     */
    @SculkStable
    public fun start(slowPeriodTicks: Long = 4, fastPeriodTicks: Long = 1) {
        handles += scheduler.runSyncRepeating(slowPeriodTicks, slowPeriodTicks) { refresh(wearers.keys) }
        handles += scheduler.runSyncRepeating(fastPeriodTicks, fastPeriodTicks) { refresh(animated) }
    }

    /**
     * Shows a tag above [player], with [lines] recomputed on each pass.
     *
     * Set [animate] only for tags that actually change every tick; it moves this wearer onto the
     * fast pass.
     */
    @SculkStable
    public fun show(player: Player, animate: Boolean = false, lines: (Player) -> List<String>) {
        val wearer = Wearer(
            entityId = entities.reserveEntityId(),
            lines = lines,
        )
        wearers[player.uniqueId] = wearer
        if (animate) animated += player.uniqueId else animated -= player.uniqueId
        render(player, wearer, force = true)
    }

    /** Removes [player]'s tag. */
    @SculkStable
    public fun hide(player: Player) {
        val wearer = wearers.remove(player.uniqueId) ?: return
        animated -= player.uniqueId
        for (viewer in viewersOf(player)) {
            entities.despawn(viewer, listOf(wearer.entityId))
        }
    }

    /** Clears [broken] and re-renders everything. Call after fixing the template. */
    @SculkStable
    public fun reset() {
        broken = false
        wearers.keys.forEach { uuid -> playerOf(uuid)?.let { render(it, wearers[uuid] ?: return@let, force = true) } }
    }

    /**
     * What [player]'s tag currently is, as the renderer sees it.
     *
     * An in-game diagnostic: "my nametag animates about once a second" and "my nametag does not
     * animate" look identical from a chair, and this is the difference.
     */
    @SculkStable
    public fun describe(player: Player): List<String> {
        val wearer = wearers[player.uniqueId] ?: return listOf("no tag")
        return listOf(
            "entity id: ${wearer.entityId}",
            "animated: ${player.uniqueId in animated}",
            "broken: $broken",
            "lines: ${wearer.lastInput ?: "(not yet rendered)"}",
        )
    }

    override fun close() {
        handles.asReversed().forEach { it.close() }
        handles.clear()
        for ((uuid, wearer) in wearers) {
            playerOf(uuid)?.let { player ->
                viewersOf(player).forEach { entities.despawn(it, listOf(wearer.entityId)) }
            }
        }
        wearers.clear()
        animated.clear()
    }

    private fun refresh(uuids: Collection<UUID>) {
        if (broken || !entities.available) return
        for (uuid in uuids) {
            val player = playerOf(uuid) ?: continue
            val wearer = wearers[uuid] ?: continue
            render(player, wearer, force = false)
        }
    }

    private fun render(player: Player, wearer: Wearer, force: Boolean) {
        if (broken) return

        val lines = try {
            wearer.lines(player)
        } catch (error: Exception) {
            broken = true
            logger.warning(
                "[SculkVisual] A nametag template threw; nametags are disabled until reset() is called. " +
                    "Without this the same failure would log once per player per tick: ${error.message}",
            )
            return
        }

        // Compared by value, not by rendering: the input list is the cheap comparison, and if it
        // has not changed the component cannot have either.
        if (!force && lines == wearer.lastInput) return
        wearer.lastInput = lines

        val text = build(lines)
        // Components are compared with equals, never toString(). TextComponentImpl.toString() is
        // expensive enough to show up in a profile, and it also loses click and hover data, so two
        // different components can serialise identically and be treated as unchanged.
        if (!force && text == wearer.lastRendered) return
        wearer.lastRendered = text

        for (viewer in viewersOf(player)) {
            if (wearer.spawnedFor.add(viewer.uniqueId)) {
                entities.spawnTextDisplay(viewer, wearer.entityId, player.location, text, style.style)
                // Mounting is what makes the client interpolate it; without this the tag has to be
                // teleported every tick and visibly trails the player.
                entities.mount(viewer, player.entityId, listOf(wearer.entityId))
            } else {
                entities.updateTextDisplay(viewer, wearer.entityId, text, style.style)
            }
        }
    }

    private fun build(lines: List<String>): Component {
        if (lines.isEmpty()) return Component.empty()
        var text = messages.render(lines.first())
        for (line in lines.drop(1)) {
            text = text.append(Component.newline()).append(messages.render(line))
        }
        return text
    }

    private fun viewersOf(wearer: Player): Collection<Player> {
        val viewers = onlinePlayers().filter { it.world == wearer.world }
        return if (style.visibleToSelf) viewers else viewers.filter { it.uniqueId != wearer.uniqueId }
    }

    private class Wearer(val entityId: Int, val lines: (Player) -> List<String>) {
        val spawnedFor: MutableSet<UUID> = HashSet()
        var lastInput: List<String>? = null
        var lastRendered: Component? = null
    }
}
