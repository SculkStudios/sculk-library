package studio.sculk.example

import java.util.UUID

public data class StaffSession(
    val uuid: UUID,
    var enabled: Boolean = false,
    var vanished: Boolean = false,
    var lastToggleAt: Long = 0L,
)

public data class FreezeRecord(
    val target: UUID,
    val staff: UUID,
    val reason: String,
    val createdAt: Long,
)
