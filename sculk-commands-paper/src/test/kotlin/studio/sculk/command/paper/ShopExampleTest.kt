package studio.sculk.command.paper

import org.bukkit.event.player.PlayerInteractEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.command.CommandNode
import studio.sculk.command.command
import studio.sculk.event.listen
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.paper.hasPermission
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup

object ShopPermissions : PermissionGroup("shop") {
    val buy = permission("buy", "Buy from the shop", PermissionDefault.EVERYONE)
    val admin = permission("admin", "Change prices and stock")
    val restock = permission("restock", "Restock the shop", PermissionDefault.EVERYONE)
    val all = permission("*", "Every shop permission", children = listOf(buy, admin, restock))
}

val shopCommand = command("shop") {
    permission(ShopPermissions.buy)
    executes { reply("<primary>The shop is open.") }
    literal("restock") {
        permission(ShopPermissions.restock.where(PermissionContext.GAME_MODE, "creative"))
        player { reply("<success>Restocked.") }
    }
    literal("prices") {
        permission(ShopPermissions.admin)
        executes { reply("<muted>Prices are set in prices.yml.") }
    }
}

class ShopPlugin : SculkPaperPlugin() {
    override suspend fun start() {
        sculk.permissions.register(ShopPermissions)
        sculk.commands().register(shopCommand)
        sculk.events.listen<PlayerInteractEvent> { event ->
            if (!event.player.hasPermission(ShopPermissions.buy)) event.isCancelled = true
        }
    }
}

class ShopExampleTest {
    @Test
    fun `each branch of the example command requires the descriptor it declares`() {
        val branches = shopCommand.root.children.filterIsInstance<CommandNode.Literal>().associate { it.name to it.requirement.check }
        assertEquals(ShopPermissions.buy, shopCommand.root.requirement.check)
        assertEquals(ShopPermissions.admin, branches["prices"])
        assertEquals(setOf(PermissionContext.GAME_MODE), branches["restock"]?.contextKeys)
    }
}
