package studio.sculk.inventory

import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction

/** What happens to one click while a menu is open. */
internal enum class Verdict {
    /** Refused; nothing moves and no handler runs. */
    CANCEL,

    /** Refused like [CANCEL], and the clicked menu slot's handler runs. */
    BUTTON,

    /** Allowed: a click in the player's own inventory that cannot reach the menu. */
    ALLOW,
}

/**
 * The locked interaction policy: nothing enters or leaves a menu except through its handlers. Every
 * route an item can take into the top inventory is closed, including the ones that start in the
 * player's own inventory (shift-click, double-click collect, number keys and the offhand key, drags
 * spanning both inventories).
 */
internal object InteractionPolicy {
    // A second event of a double click; the first already ran the handler.
    private val NO_HANDLER =
        setOf(ClickType.DOUBLE_CLICK, ClickType.CREATIVE, ClickType.WINDOW_BORDER_LEFT, ClickType.WINDOW_BORDER_RIGHT, ClickType.UNKNOWN)

    // From the player's own inventory these reach into the menu.
    private val REACHES_MENU = setOf(
        InventoryAction.MOVE_TO_OTHER_INVENTORY,
        InventoryAction.COLLECT_TO_CURSOR,
        InventoryAction.HOTBAR_SWAP,
        InventoryAction.UNKNOWN,
    )

    fun click(rawSlot: Int, menuSize: Int, action: InventoryAction, click: ClickType, allowPlayerInventory: Boolean): Verdict = when {
        rawSlot in 0 until menuSize -> if (click in NO_HANDLER) Verdict.CANCEL else Verdict.BUTTON
        !allowPlayerInventory -> Verdict.CANCEL
        action in REACHES_MENU || action.name == "HOTBAR_MOVE_AND_READD" -> Verdict.CANCEL
        else -> Verdict.ALLOW
    }

    fun drag(rawSlots: Set<Int>, menuSize: Int, allowPlayerInventory: Boolean): Verdict =
        if (!allowPlayerInventory || rawSlots.any { it < menuSize }) Verdict.CANCEL else Verdict.ALLOW
}
