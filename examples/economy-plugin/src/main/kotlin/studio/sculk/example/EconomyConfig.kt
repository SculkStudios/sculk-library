package studio.sculk.example

import studio.sculk.config.annotation.ConfigFile
import studio.sculk.config.annotation.Min

@ConfigFile("economy.yml")
public data class EconomySettings(
    @param:Min(0)
    val startingCoins: Long = 100,
    @param:Min(1)
    val topLimit: Int = 10,
    val messages: EconomyMessages = EconomyMessages(),
)

public data class EconomyMessages(
    val balance: String = "<gold>You have <yellow><coins></yellow> coins.",
    val otherBalance: String = "<aqua><player></aqua><gray> has <yellow><coins></yellow> coins.",
    val paid: String = "<green>Paid <yellow><amount></yellow> coins to <aqua><player></aqua>.",
    val received: String = "<green>You received <yellow><amount></yellow> coins from <aqua><player></aqua>.",
    val reloaded: String = "<green>Economy config reloaded.",
)
