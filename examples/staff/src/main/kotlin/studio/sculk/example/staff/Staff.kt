package studio.sculk.example.staff

import kotlinx.serialization.Serializable
import studio.sculk.config.Comment
import studio.sculk.config.NotBlank
import studio.sculk.permission.PermissionGroup
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class StaffSettings(
    @Comment("The reasons staff can pick when they remove a player.")
    val reasons: List<String> = listOf("Cheating", "Griefing", "Spam", "Harassment"),
    @Comment("What a removed player reads; <reason> is the reason picked.")
    @NotBlank
    val kickMessage: String = "<danger>You were removed from the server.\n<muted>Reason: <reason>",
    @Comment("Whether everyone is told when staff remove a player.")
    val announce: Boolean = true,
    @Comment("What a frozen player is told.")
    @NotBlank
    val freezeMessage: String = "<warning>You are frozen. Wait for a member of staff.",
)

object StaffPermissions : PermissionGroup("staff") {
    val use = permission("use", "Open the staff menu and act on players")
    val config = permission("config", "Change the staff settings in game")
}

/** A note a member of staff left about a player. */
data class Note(val author: String, val text: String)

/**
 * Who is frozen and what staff noted, shared by every thread: menus and commands run on the
 * staff member's thread, movement checks on each player's own.
 */
class Staff {
    private val frozen: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val notes = ConcurrentHashMap<UUID, MutableList<Note>>()

    fun isFrozen(player: UUID): Boolean = player in frozen

    /** Freezes [player] when they move freely and frees them when frozen; true when they are now frozen. */
    fun toggleFreeze(player: UUID): Boolean = if (frozen.remove(player)) false else frozen.add(player)

    fun note(player: UUID, note: Note) {
        notes.computeIfAbsent(player) { CopyOnWriteArrayList() } += note
    }

    fun notes(player: UUID): List<Note> = notes[player]?.toList().orEmpty()
}
