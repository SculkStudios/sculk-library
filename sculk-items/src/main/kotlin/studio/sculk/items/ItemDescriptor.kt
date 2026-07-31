package studio.sculk.items

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.text.SculkMessages

/**
 * A config-shaped item: everything a server owner would reasonably write in YAML, and nothing that
 * only makes sense in code.
 */
@SculkStable
public data class ItemDescriptor(
    public val material: String,
    public val name: String? = null,
    public val lore: List<String> = emptyList(),
    public val amount: Int = 1,
    public val enchantments: Map<String, Int> = emptyMap(),
    public val glint: Boolean = false,
    public val model: String? = null,
    public val customModelData: Int? = null,
    public val hideVanillaTooltip: Boolean = false,
    public val unbreakable: Boolean = false,
    public val data: Map<String, String> = emptyMap(),
)

/** Builds the described item, reporting an unknown material by name. */
@SculkStable
public fun ItemDescriptor.toItemStack(messages: SculkMessages = SculkMessages()): SculkResult<ItemStack> = messages.item(material) {
    amount(this@toItemStack.amount)
    this@toItemStack.name?.let { name(it) }
    if (lore.isNotEmpty()) lore(lore)
    enchantments.forEach { (key, level) -> enchantmentByKey(key)?.let { enchant(it, level) } }
    if (glint) glint()
    model?.let { model(it) }
    customModelData?.let { customModelData(it) }
    if (hideVanillaTooltip) hideVanillaTooltip()
    unbreakable(unbreakable)
    data.forEach { (key, value) -> pdc(key, value) }
}

/** Describes an existing stack in the same shape a config would use. */
@SculkStable
public fun ItemStack.toDescriptor(): ItemDescriptor {
    val meta = itemMeta
    return ItemDescriptor(
        material = type.key.key,
        name = meta?.displayName()?.let(::serializeOrNull),
        lore = meta?.lore()?.mapNotNull(::serializeOrNull).orEmpty(),
        amount = amount,
        enchantments = enchantments.mapKeys { it.key.key.key },
        glint = meta?.enchantmentGlintOverride == true,
        customModelData = meta
            ?.customModelDataComponent
            ?.floats
            ?.firstOrNull()
            ?.toInt()
            ?.takeIf { it > 0 },
        unbreakable = meta?.isUnbreakable == true,
    )
}

private val miniMessage = MiniMessage.miniMessage()

private fun serializeOrNull(component: Component): String? = miniMessage.serialize(component).takeIf { it.isNotBlank() }
