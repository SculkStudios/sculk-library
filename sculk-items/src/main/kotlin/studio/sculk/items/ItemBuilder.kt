package studio.sculk.items

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import io.papermc.paper.datacomponent.item.FoodProperties
import io.papermc.paper.datacomponent.item.ItemEnchantments
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

/**
 * Kotlin-first builder for modern Paper item stacks.
 *
 * Built entirely on Paper's data-component API (Minecraft 1.20.5+). Display properties — name,
 * lore, enchantments, model data, rarity, durability — are written as data components. For
 * components without a dedicated DSL method (food, tool, equippable, consumable, tooltip display,
 * …) use the generic [component] / [unsetComponent] escape hatch, which accepts any
 * [DataComponentType] and its value so the full modern surface is always reachable.
 *
 * ```kotlin
 * item(Material.GOLDEN_APPLE) {
 *     name("<gold>Healing Apple")
 *     lore("<gray>Restores health")
 *     component(DataComponentTypes.FOOD, FoodProperties.food().nutrition(8).saturation(4f).build())
 * }
 * ```
 */
public open class ItemBuilder public constructor(
    private var material: Material,
) {
    private var displayName: Component? = null
    private var itemName: Component? = null
    private val lore: MutableList<Component> = mutableListOf()
    private var amount: Int = 1
    private var glint: Boolean? = null
    private var customModelData: Int? = null
    private var unbreakable: Boolean? = null
    private var damage: Int? = null
    private var maxDamage: Int? = null
    private var maxStackSize: Int? = null
    private var rarity: ItemRarity? = null
    private val enchantments: MutableMap<Enchantment, Int> = linkedMapOf()
    private val flags: MutableSet<ItemFlag> = linkedSetOf()
    private val persistentData: MutableList<ItemMeta.() -> Unit> = mutableListOf()
    private val metaEdits: MutableList<ItemMeta.() -> Unit> = mutableListOf()
    private val componentEdits: MutableList<ItemStack.() -> Unit> = mutableListOf()

    /** Replaces this item's material. */
    public fun material(material: Material) {
        this.material = material
    }

    /** Replaces this item's material from a modern Minecraft key. */
    public fun material(key: String) {
        material = requireMaterial(key)
    }

    /** Sets the MiniMessage display name (the anvil-style custom name). */
    public fun name(value: String) {
        displayName = parseItemText(value)
    }

    /** Sets the Adventure display name. */
    public fun name(component: Component) {
        displayName = component
    }

    /** Sets the MiniMessage item name (the non-italic base name shown when there is no custom name). */
    public fun itemName(value: String) {
        itemName = parseItemText(value)
    }

    /** Adds MiniMessage lore lines. */
    public fun lore(vararg lines: String) {
        lore.addAll(lines.map(::parseItemText))
    }

    /** Adds MiniMessage lore lines. */
    public fun lore(lines: Iterable<String>) {
        lore.addAll(lines.map(::parseItemText))
    }

    /** Adds Adventure lore lines. */
    public fun lore(vararg lines: Component) {
        lore.addAll(lines)
    }

    /** Sets the stack amount. */
    public fun amount(value: Int) {
        require(value in 1..99) { "Item amount must be between 1 and 99." }
        amount = value
    }

    /** Adds an enchantment, allowing unsafe display levels. */
    public fun enchant(
        enchantment: Enchantment,
        level: Int,
    ) {
        require(level > 0) { "Enchantment level must be positive." }
        enchantments[enchantment] = level
    }

    /** Adds an enchantment by modern Minecraft key. */
    public fun enchant(
        key: String,
        level: Int,
    ) {
        enchant(requireEnchantment(key), level)
    }

    /** Adds item flags (hides tooltip sections such as enchantments or attributes). */
    public fun flag(vararg flags: ItemFlag) {
        this.flags += flags
    }

    /** Sets whether this item is unbreakable. */
    public fun unbreakable(value: Boolean = true) {
        unbreakable = value
    }

    /** Sets Paper's enchantment glint override without fake enchantments. */
    public fun glint(value: Boolean = true) {
        glint = value
    }

    /**
     * Sets custom model data. The DSL takes an integer for the common resource-pack workflow;
     * it is written to the modern custom-model-data component as a single float.
     */
    public fun customModelData(value: Int) {
        require(value > 0) { "Custom model data must be positive." }
        customModelData = value
    }

    /** Sets the current durability damage. */
    public fun damage(value: Int) {
        require(value >= 0) { "Damage cannot be negative." }
        damage = value
    }

    /** Overrides the maximum durability of this item. */
    public fun maxDamage(value: Int) {
        require(value > 0) { "Max damage must be positive." }
        maxDamage = value
    }

    /** Overrides the maximum stack size (1–99). */
    public fun maxStackSize(value: Int) {
        require(value in 1..99) { "Max stack size must be between 1 and 99." }
        maxStackSize = value
    }

    /** Sets the item rarity (controls default name colour). */
    public fun rarity(value: ItemRarity) {
        rarity = value
    }

    /**
     * Makes this item edible by setting the modern food component.
     *
     * For tool, equippable, consumable, or any other component without a dedicated method, use the
     * generic [component] escape hatch with the matching [DataComponentTypes] value.
     */
    public fun food(
        nutrition: Int,
        saturation: Float,
        canAlwaysEat: Boolean = false,
    ) {
        componentEdits += {
            setData(
                DataComponentTypes.FOOD,
                FoodProperties
                    .food()
                    .nutrition(nutrition)
                    .saturation(saturation)
                    .canAlwaysEat(canAlwaysEat)
                    .build(),
            )
        }
    }

    // -- Generic data-component escape hatch -----------------------------------

    /** Sets any valued data component. The modern way to reach food, tool, equippable, etc. */
    public fun <T : Any> component(
        type: DataComponentType.Valued<T>,
        value: T,
    ) {
        componentEdits += { setData(type, value) }
    }

    /** Sets a non-valued (marker) data component. */
    public fun component(type: DataComponentType.NonValued) {
        componentEdits += { setData(type) }
    }

    /** Removes a data component from the item. */
    public fun unsetComponent(type: DataComponentType) {
        componentEdits += { unsetData(type) }
    }

    // -- Persistent data -------------------------------------------------------

    /** Sets a persistent data value of any [PersistentDataType], including lists and nested containers. */
    public fun <P : Any, C : Any> pdc(
        key: NamespacedKey,
        type: PersistentDataType<P, C>,
        value: C,
    ) {
        persistentData += { persistentDataContainer.set(key, type, value) }
    }

    /** Sets a persistent data value of any [PersistentDataType] using a string key. */
    public fun <P : Any, C : Any> pdc(
        key: String,
        type: PersistentDataType<P, C>,
        value: C,
    ): Unit = pdc(ItemKeys.of(key), type, value)

    public fun pdc(
        key: NamespacedKey,
        value: String,
    ): Unit = pdc(key, PersistentDataType.STRING, value)

    public fun pdc(
        key: NamespacedKey,
        value: Int,
    ): Unit = pdc(key, PersistentDataType.INTEGER, value)

    public fun pdc(
        key: NamespacedKey,
        value: Long,
    ): Unit = pdc(key, PersistentDataType.LONG, value)

    public fun pdc(
        key: NamespacedKey,
        value: Double,
    ): Unit = pdc(key, PersistentDataType.DOUBLE, value)

    public fun pdc(
        key: NamespacedKey,
        value: Boolean,
    ): Unit = pdc(key, PersistentDataType.BOOLEAN, value)

    public fun pdc(
        key: String,
        value: String,
    ): Unit = pdc(ItemKeys.of(key), value)

    public fun pdc(
        key: String,
        value: Int,
    ): Unit = pdc(ItemKeys.of(key), value)

    public fun pdc(
        key: String,
        value: Long,
    ): Unit = pdc(ItemKeys.of(key), value)

    public fun pdc(
        key: String,
        value: Double,
    ): Unit = pdc(ItemKeys.of(key), value)

    public fun pdc(
        key: String,
        value: Boolean,
    ): Unit = pdc(ItemKeys.of(key), value)

    /** Escape hatch for advanced Paper item metadata not covered by components or PDC helpers. */
    public fun meta(block: ItemMeta.() -> Unit) {
        metaEdits += block
    }

    /** Builds the final [ItemStack]. */
    public open fun build(): ItemStack {
        val stack = ItemStack(material, amount)

        // Persistent data + escape-hatch meta edits go through ItemMeta (the PDC carrier).
        if (persistentData.isNotEmpty() || metaEdits.isNotEmpty() || flags.isNotEmpty()) {
            val meta = stack.itemMeta
            if (meta != null) {
                if (flags.isNotEmpty()) meta.addItemFlags(*flags.toTypedArray())
                persistentData.forEach { meta.it() }
                metaEdits.forEach { meta.it() }
                stack.itemMeta = meta
            }
        }

        // Everything else is written as modern data components.
        displayName?.let { stack.setData(DataComponentTypes.CUSTOM_NAME, it) }
        itemName?.let { stack.setData(DataComponentTypes.ITEM_NAME, it) }
        if (lore.isNotEmpty()) {
            val builder = ItemLore.lore()
            lore.forEach { builder.addLine(it) }
            stack.setData(DataComponentTypes.LORE, builder.build())
        }
        if (enchantments.isNotEmpty()) {
            val builder = ItemEnchantments.itemEnchantments()
            enchantments.forEach { (enchantment, level) -> builder.add(enchantment, level) }
            stack.setData(DataComponentTypes.ENCHANTMENTS, builder.build())
        }
        glint?.let { stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, it) }
        customModelData?.let {
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloat(it.toFloat()).build())
        }
        unbreakable?.let { if (it) stack.setData(DataComponentTypes.UNBREAKABLE) else stack.unsetData(DataComponentTypes.UNBREAKABLE) }
        maxStackSize?.let { stack.setData(DataComponentTypes.MAX_STACK_SIZE, it) }
        maxDamage?.let { stack.setData(DataComponentTypes.MAX_DAMAGE, it) }
        damage?.let { stack.setData(DataComponentTypes.DAMAGE, it) }
        rarity?.let { stack.setData(DataComponentTypes.RARITY, it) }
        componentEdits.forEach { stack.it() }
        return stack
    }
}

internal fun materialByKey(key: String): Material? {
    val normalized = normalizeLookupKey(key)
    val candidates =
        listOf(
            normalized,
            normalized.replace(' ', '_'),
            normalized.replace('-', '_'),
            normalized.replace('.', '_'),
        ).distinct()
    return candidates.firstNotNullOfOrNull { Material.matchMaterial(it.uppercase()) ?: Material.matchMaterial(it) }
}

internal fun requireMaterial(key: String): Material = materialByKey(key) ?: throw IllegalArgumentException("Unknown material key '$key'.")

internal fun enchantmentByKey(key: String): Enchantment? {
    val registry =
        RegistryAccess
            .registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
    val normalized = normalizeLookupKey(key)
    val candidates =
        listOf(
            normalized,
            normalized.replace(' ', '_'),
            normalized.replace('-', '_'),
            normalized.replace('.', '_'),
            commonEnchantmentAliases[normalized],
        ).filterNotNull().distinct()
    return candidates.firstNotNullOfOrNull { registry.get(NamespacedKey.minecraft(it)) }
}

internal fun requireEnchantment(key: String): Enchantment =
    enchantmentByKey(key) ?: throw IllegalArgumentException("Unknown enchantment key '$key'.")

internal fun normalizeLookupKey(key: String): String = key.trim().lowercase().substringAfter(':')

private val commonEnchantmentAliases: Map<String, String> =
    mapOf(
        "damage_all" to "sharpness",
        "arrow_fire" to "flame",
        "arrow_damage" to "power",
        "arrow_knockback" to "punch",
        "durability" to "unbreaking",
        "loot_bonus_blocks" to "fortune",
        "loot_bonus_mobs" to "looting",
        "protection_environmental" to "protection",
    )
