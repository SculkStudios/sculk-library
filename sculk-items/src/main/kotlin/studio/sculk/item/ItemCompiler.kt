package studio.sculk.item

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import io.papermc.paper.datacomponent.item.DyedItemColor
import io.papermc.paper.datacomponent.item.ItemEnchantments
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import studio.sculk.annotation.SculkInternal
import studio.sculk.pdc.PdcCodec
import studio.sculk.pdc.PdcKey
import studio.sculk.text.Text

/** The parts of compiling a spec that differ across Paper versions (ADR-0028). Not API. */
@SculkInternal
public interface ItemAdapter {
    public fun unbreakable(stack: ItemStack)

    /** Hides [parts], or the whole tooltip when [tooltip] is set, after every other component is written. */
    public fun hide(stack: ItemStack, tooltip: Boolean, parts: Set<TooltipPart>)
}

/** Compiles [ItemSpec]s. Components whose API is the same on every supported version are written here. */
internal object ItemCompiler {
    /** Written on every stack of a spec with an id; read by [CustomItems.identify]. */
    val ID: PdcKey<Key> = PdcKey(org.bukkit.NamespacedKey("sculk", "item"), PdcCodec.KEY)

    private const val TOOLTIP_DISPLAY = "io.papermc.paper.datacomponent.item.TooltipDisplay"
    private const val MODERN_ADAPTER = "studio.sculk.item.adapter.ModernItemAdapter"

    // Chosen once, by whether the server has TooltipDisplay (1.21.5+). The modern adapter is compiled
    // against the newest anchor and is never linked on a server without it.
    private val adapter: ItemAdapter by lazy {
        val loader = ItemCompiler::class.java.classLoader
        val modern = runCatching { Class.forName(TOOLTIP_DISPLAY, false, loader) }.isSuccess
        if (modern) {
            Class.forName(
                MODERN_ADAPTER,
                true,
                loader,
            ).getDeclaredConstructor().newInstance() as ItemAdapter
        } else {
            LegacyItemAdapter()
        }
    }

    fun enchantment(key: Key): Enchantment? = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key)

    fun create(spec: ItemSpec, text: Text, resolver: TagResolver): ItemStack {
        require(spec.type.isItem) { "${spec.type} is not an item" }
        val stack = ItemStack.of(spec.type, spec.amount)
        spec.name?.let { stack.setData(DataComponentTypes.ITEM_NAME, text.render(it, resolver)) }
        if (spec.lore.isNotEmpty()) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(spec.lore.map { line -> plainStyle(text.render(line, resolver)) }))
        }
        if (spec.enchantments.isNotEmpty()) {
            val enchantments = ItemEnchantments.itemEnchantments()
            spec.enchantments.forEach { (key, level) ->
                enchantments.add(requireNotNull(enchantment(key)) { "no enchantment $key is registered" }, level)
            }
            stack.setData(DataComponentTypes.ENCHANTMENTS, enchantments.build())
        }
        spec.glint?.let { stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, it) }
        spec.model?.let { stack.setData(DataComponentTypes.ITEM_MODEL, it) }
        spec.customModel?.let { model ->
            stack.setData(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloats(
                    model.floats,
                ).addFlags(model.flags).addStrings(model.strings).addColors(model.colors).build(),
            )
        }
        spec.maxStackSize?.let { stack.setData(DataComponentTypes.MAX_STACK_SIZE, it) }
        spec.maxDamage?.let { stack.setData(DataComponentTypes.MAX_DAMAGE, it) }
        spec.damage?.let { stack.setData(DataComponentTypes.DAMAGE, it) }
        spec.rarity?.let { stack.setData(DataComponentTypes.RARITY, it) }
        spec.dyedColor?.let { stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor().color(it).build()) }
        if (spec.unbreakable) adapter.unbreakable(stack)
        if (spec.hideTooltip || spec.hidden.isNotEmpty()) adapter.hide(stack, spec.hideTooltip, spec.hidden)
        if (spec.id != null || spec.data.isNotEmpty()) {
            stack.editPersistentDataContainer { container ->
                spec.id?.let { container.set(ID.key, ID.codec.type, it) }
                spec.data.forEach { it.writeTo(container) }
            }
        }
        return stack
    }

    // Lore is italic unless told otherwise; a template that sets italic itself keeps it.
    private fun plainStyle(line: Component): Component = line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
}
