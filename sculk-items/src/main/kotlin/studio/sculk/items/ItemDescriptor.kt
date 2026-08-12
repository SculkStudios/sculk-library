package studio.sculk.items

import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.text.SculkMessages

/**
 * A config-shaped item: everything a server owner would reasonably write in YAML, and nothing that
 * only makes sense in code.
 *
 * Serialisable because "put it in a config" is the entire point: `sculk-config` decodes through the
 * compiler-generated descriptor, so a plugin embedding one of these in its own `@Serializable`
 * settings class needs a serializer to embed. Without one the type reads as documentation that does
 * not compile.
 */
@Serializable
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

/**
 * Builds the described item, reporting an unknown material by name.
 *
 * `material` is whatever a server owner wrote: a vanilla key, or `nexo:ruby_sword` and friends for a
 * custom item, which arrives as a finished stack that the rest of the descriptor is written on top of.
 */
@SculkStable
public fun ItemDescriptor.toItemStack(messages: SculkMessages = SculkMessages()): SculkResult<ItemStack> =
    messages.item(material) { applyDescriptor(this@toItemStack) }

/**
 * Applies a descriptor to a builder.
 *
 * Split out of [toItemStack] so it can be asserted on directly. Whether a property was *set* and
 * what the finished item *looks like* are not the same question, and only the first one distinguishes
 * the 1.21.4 crash from correct behaviour.
 */
internal fun ItemBuilder.applyDescriptor(descriptor: ItemDescriptor) {
    amount(descriptor.amount)
    descriptor.name?.let { name(it) }
    if (descriptor.lore.isNotEmpty()) lore(descriptor.lore)
    descriptor.enchantments.forEach { (key, level) -> enchantmentByKey(key)?.let { enchant(it, level) } }
    if (descriptor.glint) glint()
    descriptor.model?.let { model(it) }
    descriptor.customModelData?.let { customModelData(it) }
    if (descriptor.hideVanillaTooltip) hideVanillaTooltip()
    // Only when asked. Setting the default was pointless work on every server and a hard crash on
    // 1.21.4, where UNBREAKABLE is a differently-shaped field that the compiled reference cannot
    // resolve — so every item built from config threw NoSuchFieldError.
    if (descriptor.unbreakable) unbreakable(true)
    descriptor.data.forEach { (key, value) -> pdc(key, value) }
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
