package studio.sculk.example

import studio.sculk.data.orm.PrimaryKey
import studio.sculk.data.orm.Table
import java.util.UUID

@Table("economy_accounts")
public data class EconomyAccount(
    @PrimaryKey val uuid: UUID,
    var name: String,
    var coins: Long,
    var createdAt: Long,
    var updatedAt: Long,
)
