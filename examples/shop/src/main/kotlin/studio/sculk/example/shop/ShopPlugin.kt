package studio.sculk.example.shop

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import studio.sculk.annotation.SculkExperimental
import studio.sculk.command.command
import studio.sculk.command.paper.commands
import studio.sculk.config.ConfigFile
import studio.sculk.config.SculkConfig
import studio.sculk.dialog.Dialogs
import studio.sculk.dialog.dialog
import studio.sculk.getOrThrow
import studio.sculk.inventory.MenuLayout
import studio.sculk.inventory.MenuSpec
import studio.sculk.inventory.Menus
import studio.sculk.inventory.menu
import studio.sculk.inventory.tradeMenu
import studio.sculk.item.ItemSpec
import studio.sculk.item.item
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.text.Text

/**
 * A shop on Sculk, paid in emeralds: `/shop` opens a paged menu of offers from the config, buying
 * asks how many in a dialog (or confirms one in a small menu where dialogs do not exist), and
 * `/trader` opens a villager's trading screen with the configured trades.
 */
class ShopPlugin : SculkPaperPlugin() {
    private lateinit var settings: ConfigFile<ShopSettings>
    private lateinit var menus: Menus
    private lateinit var dialogs: Dialogs
    private val messages = Text()

    private data class Page(val number: Int = 0)

    override suspend fun start() {
        settings = SculkConfig.load<ShopSettings>(dataPath.resolve("config.yml")) {
            header = listOf("Shop example for Sculk Studio.")
            validate { shop ->
                (shop.offers + shop.trades).forEachIndexed { index, offer ->
                    ensure(offer.material() != null, "offers", "item names Minecraft knows") { "${offer.item} (entry ${index + 1})" }
                }
            }
        }.getOrThrow()
        sculk.permissions.register(ShopPermissions)
        menus = Menus(this, sculk.scope.resources)
        dialogs = Dialogs(this, messages)

        sculk.commands().register(
            command("shop") {
                description = "Buy items for emeralds"
                permission(ShopPermissions.buy)
                player { menus.open(onlinePlayer(), shop, Page()) }
            },
            command("trader") {
                description = "Trade with the travelling trader"
                permission(ShopPermissions.buy)
                player { menus.trade(onlinePlayer(), trader()) }
            },
        )
    }

    private fun studio.sculk.command.CommandContext.onlinePlayer(): Player =
        source.uniqueId?.let(Bukkit::getPlayer) ?: fail("<danger>Only players can shop.")

    private fun icon(offer: Offer): ItemSpec = item(offer.material() ?: Material.BARRIER) {
        amount = offer.amount
        lore("<muted>Costs <value><price></value> emerald(s)", "<muted>Click to buy")
        value("price", offer.price)
    }

    private val previousArrow = item(Material.ARROW) { name = "<muted>Previous page" }
    private val nextArrow = item(Material.ARROW) { name = "<muted>Next page" }

    @OptIn(SculkExperimental::class)
    private val shop: MenuSpec<Page> = menu(MenuLayout.chest(4)) {
        text = messages
        title("<primary>Shop")
        render { state ->
            val page = paginate(OFFER_SLOTS, settings.value.offers, state.number, icon = ::icon) { offer ->
                close()
                dialogs.show(player, quantity(offer), mapOf("price" to offer.price, "amount" to offer.amount)).getOrThrow()
            }
            pager(page, PREVIOUS, NEXT, previousArrow, nextArrow) { number -> update { it.copy(number = number) } }
        }
    }

    private fun quantity(offer: Offer) = dialog("<primary>How many?") {
        body("Each costs <price> emerald(s) for <amount>.")
        number("quantity", "Quantity", 1f..settings.value.maxQuantity.toFloat(), step = 1f, initial = 1f)
        button("<success>Buy") { answer -> buy(answer.player, offer, answer.number("quantity")?.toInt() ?: return@button) }
        button("Cancel")
        fallback { player -> menus.open(player, confirm(offer), Unit) }
    }

    private fun confirm(offer: Offer): MenuSpec<Unit> = menu(MenuLayout.HOPPER) {
        text = messages
        title("<primary>Buy one?")
        render {
            slot(0, icon(offer))
            slot(2, item(Material.LIME_CONCRETE) { name = "<success>Buy" }) {
                close()
                buy(player, offer, 1)
            }
            slot(4, item(Material.RED_CONCRETE) { name = "<danger>Cancel" }) { close() }
        }
    }

    // Runs on the buyer's thread, where their inventory belongs.
    private fun buy(player: Player, offer: Offer, quantity: Int) {
        val material = offer.material() ?: return
        val price = cost(offer, quantity, settings.value.maxQuantity) ?: return
        val inventory = player.inventory
        if (!inventory.containsAtLeast(ItemStack(Material.EMERALD), price)) {
            player.sendMessage(messages.render("<danger>That costs <price> emeralds.", "price" to price))
            return
        }
        inventory.removeItem(ItemStack(Material.EMERALD, price))
        val left = inventory.addItem(*List(quantity) { ItemStack(material, offer.amount) }.toTypedArray())
        left.values.forEach { player.world.dropItemNaturally(player.location, it) }
        player.sendMessage(
            messages.render(
                "<success>Bought <amount> <item> for <price> emeralds.",
                "amount" to offer.amount * quantity,
                "item" to offer.item,
                "price" to price,
            ),
        )
    }

    private fun trader() = tradeMenu("<primary>Travelling trader") {
        text = messages
        settings.value.trades.forEach { offer ->
            val material = offer.material() ?: return@forEach
            trade(item(material) { amount = offer.amount }, item(Material.EMERALD) { amount = offer.price })
        }
    }

    private companion object {
        val OFFER_SLOTS = (0 until 27).toList()
        const val PREVIOUS = 27
        const val NEXT = 35
    }
}
