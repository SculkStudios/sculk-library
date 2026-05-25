package studio.sculk.items

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

/**
 * Kotlin-first builder for modern Paper item stacks.
 */
public open class ItemBuilder public constructor(
    private var material: Material,
) {
    private var displayName: Component? = null
    private val lore: MutableList<Component> = mutableListOf()
    private var amount: Int = 1
    private var glint: Boolean? = null
    private var customModelData: Int? = null
    private var unbreakable: Boolean? = null
    private var damage: Int? = null
    private val enchantments: MutableMap<Enchantment, Int> = linkedMapOf()
    private val flags: MutableSet<ItemFlag> = linkedSetOf()
    private val persistentData: MutableList<ItemMeta.() -> Unit> = mutableListOf()
    private val metaEdits: MutableList<ItemMeta.() -> Unit> = mutableListOf()

    /** Replaces this item's material. */
    public fun material(material: Material) {
        this.material = material
    }

    /** Replaces this item's material from a modern Minecraft key. */
    public fun material(key: String) {
        material = requireMaterial(key)
    }

    /** Sets the MiniMessage display name. */
    public fun name(value: String) {
        displayName = parseItemText(value)
    }

    /** Sets the Adventure display name. */
    public fun name(component: Component) {
        displayName = component
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

    /** Adds item flags. */
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
     * Sets modern custom model data.
     *
     * The public DSL uses an integer because that remains the common
     * resource-pack workflow. Internally it writes Paper's modern component
     * representation as a single float.
     */
    public fun customModelData(value: Int) {
        require(value > 0) { "Custom model data must be positive." }
        customModelData = value
    }

    /** Applies item damage if the item meta supports damage. */
    public fun damage(value: Int) {
        require(value >= 0) { "Damage cannot be negative." }
        damage = value
    }

    public fun pdc(
        key: NamespacedKey,
        value: String,
    ) {
        persistentData += { persistentDataContainer.set(key, PersistentDataType.STRING, value) }
    }

    public fun pdc(
        key: NamespacedKey,
        value: Int,
    ) {
        persistentData += { persistentDataContainer.set(key, PersistentDataType.INTEGER, value) }
    }

    public fun pdc(
        key: NamespacedKey,
        value: Long,
    ) {
        persistentData += { persistentDataContainer.set(key, PersistentDataType.LONG, value) }
    }

    public fun pdc(
        key: NamespacedKey,
        value: Double,
    ) {
        persistentData += { persistentDataContainer.set(key, PersistentDataType.DOUBLE, value) }
    }

    public fun pdc(
        key: NamespacedKey,
        value: Boolean,
    ) {
        persistentData += { persistentDataContainer.set(key, PersistentDataType.BYTE, if (value) 1 else 0) }
    }

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

    /** Escape hatch for advanced Paper item metadata. */
    public fun meta(block: ItemMeta.() -> Unit) {
        metaEdits += block
    }

    /** Builds the final [ItemStack]. */
    public open fun build(): ItemStack {
        val stack = ItemStack(material, amount)
        val meta = stack.itemMeta ?: return stack

        displayName?.let(meta::displayName)
        if (lore.isNotEmpty()) meta.lore(lore.toList())
        glint?.let(meta::setEnchantmentGlintOverride)
        customModelData?.let { applyCustomModelData(meta, it) }
        unbreakable?.let { meta.isUnbreakable = it }
        if (flags.isNotEmpty()) meta.addItemFlags(*flags.toTypedArray())
        enchantments.forEach { (enchantment, level) -> meta.addEnchant(enchantment, level, true) }
        damage?.let {
            if (meta is Damageable) meta.damage = it
        }
        persistentData.forEach { meta.it() }
        metaEdits.forEach { meta.it() }

        stack.itemMeta = meta
        return stack
    }

    private fun applyCustomModelData(
        meta: ItemMeta,
        value: Int,
    ) {
        val component = meta.customModelDataComponent
        component.setFloats(listOf(value.toFloat()))
        meta.setCustomModelDataComponent(component)
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
