package studio.sculk.example

import studio.sculk.data.orm.PrimaryKey
import studio.sculk.data.orm.Table
import java.util.UUID

@Table("kit_cooldowns")
public data class KitCooldown(@PrimaryKey val id: String, val uuid: UUID, val kitId: String, var lastClaimedAt: Long)

public data class KitClaimStatus(val allowed: Boolean, val remainingMillis: Long)
