package studio.sculk.example.economy

import kotlinx.serialization.Serializable
import studio.sculk.config.Comment
import studio.sculk.config.DecimalRange
import studio.sculk.config.NotBlank
import studio.sculk.config.Range
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup
import java.util.UUID

@Serializable
data class Settings(
    @Comment("The currency's name, shown after every amount.")
    @NotBlank
    val currency: String = "coins",
    @Comment("What a new account starts with.")
    @DecimalRange(min = 0.0, max = 1_000_000.0)
    val startingBalance: Double = 100.0,
    @Comment("How often balances are written to disk, in minutes.")
    @Range(min = 1, max = 60)
    val autosaveMinutes: Int = 5,
)

/** Balances on disk, keyed by player UUID. Written by the plugin, not meant for hand editing. */
@Serializable
data class SavedBalances(val balances: Map<String, Double> = emptyMap()) {
    fun toLedger(startingBalance: Double): Ledger = Ledger(startingBalance, balances.mapKeys { UUID.fromString(it.key) })

    companion object {
        fun of(ledger: Ledger): SavedBalances = SavedBalances(ledger.snapshot().mapKeys { it.key.toString() })
    }
}

object EconomyPermissions : PermissionGroup("economy") {
    val balance = permission("balance", "See your balance and others'", PermissionDefault.EVERYONE)
    val pay = permission("pay", "Send money to another player", PermissionDefault.EVERYONE)
    val admin = permission("admin", "Give, take and reset balances")
}
