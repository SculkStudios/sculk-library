package studio.sculk.hud

import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.util.UUID

/**
 * How much a message deserves the one action-bar slot.
 *
 * There is exactly one action bar and everything wants it. Without an order, the *most frequent*
 * writer wins by accident: a `+$5` on every mob kill buries "you are about to lose your claim",
 * because the last write is the one on screen.
 */
@SculkStable
public enum class ActionBarPriority {
    /** Routine confirmation of something the player just did. */
    FEEDBACK,

    /** Ongoing state — a progress bar, a countdown. Outranks feedback. */
    ACTIVITY,

    /** Something the player must see. Outranks everything. */
    ALERT,
}

/**
 * Which action-bar message each player should be seeing.
 *
 * Deliberately free of Bukkit types: this is arbitration and expiry logic, which is where the bugs
 * are, and keeping it pure means it tests in microseconds with no server.
 *
 * Messages **expire** rather than needing to be cleared. A system that sets an action bar and then
 * crashes before clearing it would otherwise pin its text on screen for the rest of the session.
 */
@SculkInternal
public class ActionBarState {
    private val entries = HashMap<UUID, MutableList<Entry>>()

    /**
     * Records a message for [player], to show until [currentTick] + [durationTicks].
     *
     * A message of the same priority replaces the previous one — a progress bar should update, not
     * queue behind itself.
     */
    public fun show(player: UUID, text: HudRow, priority: ActionBarPriority, durationTicks: Long, currentTick: Long) {
        val list = entries.getOrPut(player) { mutableListOf() }
        list.removeIf { it.priority == priority }
        list += Entry(text, priority, currentTick + durationTicks)
    }

    /** The message [player] should see now, or null when there is nothing left to show. */
    public fun current(player: UUID, currentTick: Long): HudRow? {
        val list = entries[player] ?: return null
        list.removeIf { it.expiresAtTick <= currentTick }
        if (list.isEmpty()) {
            entries.remove(player)
            return null
        }
        return list.maxBy { it.priority.ordinal }.text
    }

    /** Drops everything for [player]. Call on quit, or the map grows for the server's uptime. */
    public fun clear(player: UUID) {
        entries.remove(player)
    }

    /** Drops only [priority] for [player], e.g. when an activity finishes early. */
    public fun clear(player: UUID, priority: ActionBarPriority) {
        entries[player]?.let { list ->
            list.removeIf { it.priority == priority }
            if (list.isEmpty()) entries.remove(player)
        }
    }

    /** Players with at least one live message. Exposed so the driver can skip the rest. */
    public val trackedPlayers: Set<UUID> get() = entries.keys.toSet()

    private class Entry(val text: HudRow, val priority: ActionBarPriority, val expiresAtTick: Long)
}
