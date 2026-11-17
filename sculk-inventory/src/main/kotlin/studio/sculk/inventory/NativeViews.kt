package studio.sculk.inventory

import io.papermc.paper.event.player.PlayerPurchaseEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MenuType
import org.bukkit.inventory.MerchantInventory
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.inventory.view.AnvilView
import org.bukkit.plugin.Plugin
import studio.sculk.annotation.SculkStable
import studio.sculk.item.ItemSpec
import studio.sculk.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Asks a player for one line of text in an anvil: they type in its rename field, which starts as
 * [initial], and take the result to answer. What they type is plain text, never markup.
 */
@SculkStable
public class TextPrompt internal constructor(
    public val title: String,
    public val initial: String,
    internal val text: Text,
    internal val check: (String) -> String?,
    internal val answer: (Player, String) -> Unit,
    internal val cancel: ((Player) -> Unit)?,
)

/** Builds a [TextPrompt]; see [textPrompt]. */
@SculkStable
public class TextPromptBuilder internal constructor(private val title: String) {
    /** What the rename field holds when the prompt opens. */
    public var initial: String = ""

    /** Renders the title and any problem [check] reports. */
    public var text: Text = Text()

    private var check: (String) -> String? = { null }
    private var answer: ((Player, String) -> Unit)? = null
    private var cancel: ((Player) -> Unit)? = null

    /**
     * A rule for the text: null accepts it, and a message template is shown under the result while
     * the player cannot answer with what they typed.
     */
    public fun check(rule: (text: String) -> String?) {
        check = rule
    }

    /** Runs with the accepted text, on the player's thread, after the anvil has closed. */
    public fun onAnswer(action: (player: Player, text: String) -> Unit) {
        answer = action
    }

    /** Runs when the player closes the anvil without answering. */
    public fun onCancel(action: (player: Player) -> Unit) {
        cancel = action
    }

    internal fun build(): TextPrompt = TextPrompt(title, initial, text, check, requireNotNull(answer) { "a prompt needs onAnswer" }, cancel)
}

/**
 * ```kotlin
 * menus.prompt(player, textPrompt("<primary>Name your shop") {
 *     initial = "My shop"
 *     check { name -> if (name.length in 3..16) null else "<danger>3 to 16 characters" }
 *     onAnswer { player, name -> shops.rename(player, name) }
 * })
 * ```
 */
@SculkStable
public fun textPrompt(title: String, build: TextPromptBuilder.() -> Unit): TextPrompt = TextPromptBuilder(title).apply(build).build()

/** One offer of a [TradeMenu]: [ingredients], one or two, for [result], at most [maxUses] times per opening. */
@SculkStable
public class Trade internal constructor(
    public val result: ItemSpec,
    ingredients: List<ItemSpec>,
    public val maxUses: Int,
    internal val onTrade: ((Player) -> Boolean)?,
) {
    public val ingredients: List<ItemSpec> = ingredients.toList()

    init {
        require(this.ingredients.size in 1..2) { "a trade takes one or two ingredients, not ${this.ingredients.size}" }
        require(maxUses > 0) { "a trade must be usable at least once" }
    }
}

/** A villager-style trading screen with the plugin's own offers; vanilla moves the items. */
@SculkStable
public class TradeMenu internal constructor(public val title: String, internal val text: Text, trades: List<Trade>) {
    public val trades: List<Trade> = trades.toList()

    init {
        require(this.trades.isNotEmpty()) { "a trade menu needs at least one trade" }
    }
}

/** Builds a [TradeMenu]; see [tradeMenu]. */
@SculkStable
public class TradeMenuBuilder internal constructor(private val title: String) {
    public var text: Text = Text()
    private val trades = mutableListOf<Trade>()

    /**
     * Offers [result] for [ingredients]. [onTrade] runs on the player's thread before the items
     * change hands, each time; returning false refuses that trade.
     */
    public fun trade(result: ItemSpec, vararg ingredients: ItemSpec, maxUses: Int = Int.MAX_VALUE, onTrade: ((Player) -> Boolean)? = null) {
        trades += Trade(result, ingredients.toList(), maxUses, onTrade)
    }

    internal fun build(): TradeMenu = TradeMenu(title, text, trades)
}

/**
 * ```kotlin
 * menus.trade(player, tradeMenu("<primary>Fisherman") {
 *     trade(item(Material.COD) { amount = 4 }, item(Material.EMERALD))
 *     trade(item(Material.FISHING_ROD), item(Material.EMERALD) { amount = 3 }, maxUses = 1) { player -> !player.isSneaking }
 * })
 * ```
 */
@SculkStable
public fun tradeMenu(title: String, build: TradeMenuBuilder.() -> Unit): TradeMenu = TradeMenuBuilder(title).apply(build).build()

/**
 * The anvil prompts and trade menus [Menus] has open, one per player, found by the view the player
 * has open. Every event arrives on the player's thread.
 */
internal class NativeViews(private val plugin: Plugin) : Listener {
    private sealed class Open(val player: Player, val view: InventoryView) {
        class Prompt(player: Player, view: AnvilView, val prompt: TextPrompt) : Open(player, view) {
            var answered = false
        }

        class Trading(player: Player, view: InventoryView, val menu: TradeMenu) : Open(player, view)
    }

    private val open = ConcurrentHashMap<UUID, Open>()

    fun prompt(player: Player, prompt: TextPrompt) {
        val view = MenuType.ANVIL.builder().title(prompt.text.render(prompt.title)).build(player)
        // The rename field starts with the left item's name; the item never leaves the anvil.
        view.topInventory.setItem(LEFT, named(prompt.initial))
        open[player.uniqueId] = Open.Prompt(player, view, prompt)
        view.open()
    }

    fun trade(player: Player, menu: TradeMenu) {
        val merchant = Bukkit.createMerchant()
        merchant.recipes = menu.trades.map { trade ->
            MerchantRecipe(trade.result.create(menu.text), 0, trade.maxUses, false, 0, 0f).apply {
                ingredients = trade.ingredients.map { it.create(menu.text) }
            }
        }
        val view = MenuType.MERCHANT.builder().merchant(merchant).title(menu.text.render(menu.title)).build(player)
        open[player.uniqueId] = Open.Trading(player, view, menu)
        view.open()
    }

    fun closeAll() {
        open.values.toList().forEach { shown ->
            onPlayer(shown.player) {
                if (shown.player.openInventory ===
                    shown.view
                ) {
                    shown.player.closeInventory()
                }
            }
        }
    }

    private fun of(player: Player, view: InventoryView): Open? = open[player.uniqueId]?.takeIf {
        it.view.topInventory === view.topInventory
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepare(event: PrepareAnvilEvent) {
        val shown = event.viewers.firstOrNull()?.let { open[it.uniqueId] } as? Open.Prompt ?: return
        if (shown.view.topInventory !== event.inventory) return
        val text = (shown.view as AnvilView).renameText ?: shown.prompt.initial
        val problem = shown.prompt.check(text)
        event.result = named(text, problem?.let { shown.prompt.text.render(it) })
        // A cost above zero would show as levels; nothing is paid, the click is taken over below.
        event.view.repairCost = 0
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val shown = of(player, event.view) as? Open.Prompt ?: return
        event.isCancelled = true
        if (event.rawSlot != RESULT || shown.answered) return
        val text = (shown.view as AnvilView).renameText ?: shown.prompt.initial
        if (shown.prompt.check(text) != null) return
        shown.answered = true
        shown.view.topInventory.clear()
        // Closing from inside a click event is unsafe; the answer follows the close.
        onPlayer(player, later = true) {
            if (player.openInventory === shown.view) player.closeInventory()
            guarded(player, "prompt answer") { shown.prompt.answer(player, text) }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (of(player, event.view) is Open.Prompt) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPurchase(event: PlayerPurchaseEvent) {
        val shown = of(event.player, event.player.openInventory) as? Open.Trading ?: return
        val index = (shown.view.topInventory as? MerchantInventory)?.selectedRecipeIndex ?: return
        val onTrade = shown.menu.trades.getOrNull(index)?.onTrade ?: return
        val allowed = try {
            onTrade(event.player)
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "A trade handler failed for ${event.player.name}", failure)
            false
        }
        if (!allowed) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val shown = of(player, event.view) ?: return
        open.remove(player.uniqueId, shown)
        if (shown !is Open.Prompt) return
        // Runs before the anvil hands its items back, so the prompt's item is never given away.
        shown.view.topInventory.clear()
        if (!shown.answered) shown.prompt.cancel?.let { cancel -> guarded(player, "prompt cancel") { cancel(player) } }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        open.remove(event.player.uniqueId)
    }

    private fun named(name: String, problem: Component? = null): ItemStack = ItemStack(Material.PAPER).apply {
        editMeta { meta ->
            meta.displayName(Component.text(name))
            problem?.let { meta.lore(listOf(it)) }
        }
    }

    private fun guarded(player: Player, what: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            plugin.logger.log(Level.SEVERE, "A $what failed for ${player.name}", failure)
        }
    }

    private fun onPlayer(player: Player, later: Boolean = false, action: () -> Unit) {
        if (!later && Bukkit.isOwnedByCurrentRegion(player)) action() else player.scheduler.run(plugin, { action() }, null)
    }

    private companion object {
        const val LEFT = 0
        const val RESULT = 2
    }
}
