package studio.sculk.example

import studio.sculk.data.orm.PrimaryKey
import studio.sculk.data.orm.Table
import java.util.UUID

@Table("player_profiles")
public data class PlayerProfile(
    @PrimaryKey val uuid: UUID,
    var name: String,
    var firstSeen: Long,
    var lastSeen: Long,
    var joins: Int,
    var kills: Int,
    var deaths: Int,
    var coins: Long,
)
