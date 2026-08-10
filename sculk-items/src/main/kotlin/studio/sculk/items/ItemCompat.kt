package studio.sculk.items

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import studio.sculk.version.MinecraftVersion
import java.util.concurrent.ConcurrentHashMap

/**
 * What the running server's item API actually supports.
 *
 * Paper reshaped data components several times inside 1.21, and a plugin compiled against the newest
 * one still loads on the oldest — `api-version: '1.21'` covers the whole line. The differences are
 * not additive, so "newer API, same behaviour" does not hold:
 *
 * | | 1.21.0–1.21.3 | 1.21.4 | 1.21.5+ |
 * |---|---|---|---|
 * | `ItemStack.setData` | absent | present | present |
 * | `UNBREAKABLE` | — | `Valued<Unbreakable>` | `NonValued` |
 * | `TOOLTIP_DISPLAY` | — | absent | present |
 * | `ITEM_MODEL` | 1.21.2+ | present | present |
 *
 * A JVM field reference carries its type descriptor, so a `NonValued UNBREAKABLE` reference compiled
 * against 1.21.11 does not resolve against 1.21.4's `Valued` field — it throws `NoSuchFieldError`.
 * That is not a warning at load; it is a hard failure the first time the instruction runs, which for
 * [ItemBuilder.build] meant every single item on 1.21.4.
 *
 * **Why reflection probes rather than version comparisons.** The version number is a proxy for what
 * we actually need to know, and a wrong proxy on any forked or backported build. Asking the class
 * whether it has the field is the direct question. [serverVersion] exists only to make log lines
 * readable.
 *
 * **Why guarding is enough.** The JVM resolves a constant-pool entry on first *execution*, not at
 * class load or verification. So `if (unbreakableIsMarker) stack.setData(DataComponentTypes.UNBREAKABLE)`
 * is safe on a server without that field: the branch is not taken, the `getstatic` never runs, and
 * nothing resolves. Each risky component still lives in its own small method so that a surprise only
 * ever costs that one feature.
 */
internal object ItemCompat {
    private const val TYPES = "io.papermc.paper.datacomponent.DataComponentTypes"
    private const val NON_VALUED = "io.papermc.paper.datacomponent.DataComponentType\$NonValued"

    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** The running server version, for log messages only. Null when Bukkit is not up, as in tests. */
    val serverVersion: MinecraftVersion? by lazy {
        runCatching { MinecraftVersion.parse(Bukkit.getServer().minecraftVersion) }.getOrNull()
    }

    /** Whether Paper's data-component API exists at all. False on 1.21.0–1.21.3, where only `ItemMeta` does. */
    val dataComponents: Boolean by lazy {
        runCatching { ItemStack::class.java.methods.any { it.name == "setData" } }.getOrDefault(false)
    }

    /** Whether `UNBREAKABLE` is the 1.21.5+ marker component rather than 1.21.4's valued one. */
    val unbreakableIsMarker: Boolean by lazy {
        val field = componentField("UNBREAKABLE") ?: return@lazy false
        runCatching { Class.forName(NON_VALUED).isAssignableFrom(field.type) }.getOrDefault(false)
    }

    /** Whether `TOOLTIP_DISPLAY` exists — 1.21.5+, where per-component `show_in_tooltip` was consolidated. */
    val tooltipDisplay: Boolean by lazy {
        componentField("TOOLTIP_DISPLAY") != null && classPresent("io.papermc.paper.datacomponent.item.TooltipDisplay")
    }

    /** Whether `ITEM_MODEL` exists — 1.21.2+. */
    val itemModel: Boolean by lazy { componentField("ITEM_MODEL") != null }

    /** Whether custom model data is the structured 1.21.4+ component rather than a bare integer. */
    val customModelDataComponent: Boolean by lazy {
        classPresent("io.papermc.paper.datacomponent.item.CustomModelData")
    }

    /**
     * Logs once that [feature] is not available here, then never again.
     *
     * Once, because these are reached from item builders that run on every menu render — a per-call
     * warning would bury the console at several lines a second.
     */
    fun unsupported(feature: String) {
        if (!warned.add(feature)) return
        val on = serverVersion?.let { " (server is $it)" } ?: ""
        itemLogger.info("'$feature' is not supported by this Minecraft version$on; the item was built without it.")
    }

    private fun componentField(name: String): java.lang.reflect.Field? = runCatching { Class.forName(TYPES).getField(name) }.getOrNull()

    private fun classPresent(name: String): Boolean = runCatching { Class.forName(name) }.isSuccess
}
