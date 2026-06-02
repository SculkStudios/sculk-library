package studio.sculk.example

import java.util.Collections
import java.util.UUID

public class StaffToolsService(private val clock: () -> Long = System::currentTimeMillis) {
    private val sessions = Collections.synchronizedMap(linkedMapOf<UUID, StaffSession>())
    private val frozen = Collections.synchronizedMap(linkedMapOf<UUID, FreezeRecord>())

    public fun toggleStaffMode(uuid: UUID): StaffSession {
        val session = sessions.getOrPut(uuid) { StaffSession(uuid) }
        session.enabled = !session.enabled
        session.lastToggleAt = clock()
        return session
    }

    public fun freeze(staff: UUID, target: UUID, reason: String): FreezeRecord {
        val record = FreezeRecord(target, staff, reason.ifBlank { "No reason provided." }, clock())
        frozen[target] = record
        return record
    }

    public fun unfreeze(target: UUID): Boolean = frozen.remove(target) != null

    public fun frozen(target: UUID): FreezeRecord? = frozen[target]

    public fun shouldBlockMovement(target: UUID): Boolean = target in frozen

    public fun cleanup(uuid: UUID) {
        sessions.remove(uuid)
        frozen.remove(uuid)
    }

    public fun staffChatRecipients(viewers: Iterable<StaffViewer>, sender: UUID): List<StaffViewer> =
        viewers.filter { it.uuid == sender || it.hasStaffChatPermission }
}

public data class StaffViewer(val uuid: UUID, val name: String, val hasStaffChatPermission: Boolean)
