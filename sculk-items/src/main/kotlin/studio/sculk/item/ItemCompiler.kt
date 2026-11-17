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
import org.bukkit.Registry
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
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

/** Writes the components of [NewerComponent], all newer than the 1.21.4 floor (ADR-0028). Not API. */
@SculkInternal
public interface CombatAdapter {
    /** Writes each such component [spec] sets; the caller has confirmed the server has every one of them. */
    public fun write(stack: ItemStack, spec: ItemSpec)
}

/** An item component newer than the 1.21.4 floor, by the [ItemSpec] property that sets it. */
internal enum class NewerComponent(val property: String, val id: String, private val fields: List<String>) {
    WEAPON("weapon", "minecraft:weapon", listOf("WEAPON")),
    BLOCKS_ATTACKS("blocksAttacks", "minecraft:blocks_attacks", listOf("BLOCKS_ATTACKS")),
    ATTACK_RANGE("attackRange", "minecraft:attack_range", listOf("ATTACK_RANGE")),

    // Renamed attack_animation in 26.3, with the same value type.
    SWING_ANIMATION("swingAnimation", "minecraft:swing_animation", listOf("SWING_ANIMATION", "ATTACK_ANIMATION")),
    USE_EFFECTS("useEffects", "minecraft:use_effects", listOf("USE_EFFECTS")),
    PIERCING_WEAPON("piercingWeapon", "minecraft:piercing_weapon", listOf("PIERCING_WEAPON")),
    KINETIC_WEAPON("kineticWeapon", "minecraft:kinetic_weapon", listOf("KINETIC_WEAPON")),
    MINIMUM_ATTACK_CHARGE("minimumAttackCharge", "minecraft:minimum_attack_charge", listOf("MINIMUM_ATTACK_CHARGE")),
    ;

    // Probed once, by field name: main code compiles against 1.21.4, where none of these exist.
    // getField neither initialises DataComponentTypes nor needs a running server.
    val available: Boolean by lazy {
        fields.any { field -> runCatching { DataComponentTypes::class.java.getField(field) }.isSuccess }
    }
}

/** Compiles [ItemSpec]s. Components whose API is the same on every supported version are written here. */
@OptIn(SculkExperimental::class)
internal object ItemCompiler {
    /** Written on every stack of a spec with an id; read by [CustomItems.identify]. */
    val ID: PdcKey<Key> = PdcKey(org.bukkit.NamespacedKey("sculk", "item"), PdcCodec.KEY)

    private const val TOOLTIP_DISPLAY = "io.papermc.paper.datacomponent.item.TooltipDisplay"
    private const val MODERN_ADAPTER = "studio.sculk.item.adapter.ModernItemAdapter"
    private const val COMBAT_ADAPTER = "studio.sculk.item.adapter.CombatComponentAdapter"
    private const val NEWER_SINCE = "Paper 1.21.11"

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

    // Loaded by name on first use, which create() reaches only after confirming the server has every
    // newer component the spec sets, so the Paper types the adapter names exist.
    private val combat: CombatAdapter by lazy {
        Class.forName(COMBAT_ADAPTER, true, ItemCompiler::class.java.classLoader).getDeclaredConstructor().newInstance() as CombatAdapter
    }

    fun enchantment(key: Key): Enchantment? = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key)

    fun damageTypes(): Registry<DamageType> = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE)

    /** The components newer than the floor that [spec] sets, in [NewerComponent] order. */
    fun newerComponents(spec: ItemSpec): List<NewerComponent> = buildList {
        if (spec.weapon != null) add(NewerComponent.WEAPON)
        if (spec.blocksAttacks != null) add(NewerComponent.BLOCKS_ATTACKS)
        if (spec.attackRange != null) add(NewerComponent.ATTACK_RANGE)
        if (spec.swingAnimation != null) add(NewerComponent.SWING_ANIMATION)
        if (spec.useEffects != null) add(NewerComponent.USE_EFFECTS)
        if (spec.piercingWeapon != null) add(NewerComponent.PIERCING_WEAPON)
        if (spec.kineticWeapon != null) add(NewerComponent.KINETIC_WEAPON)
        if (spec.minimumAttackCharge != null) add(NewerComponent.MINIMUM_ATTACK_CHARGE)
    }

    /** The failure for the components [spec] sets that this server lacks, or null when it has them all. Needs no server. */
    fun unsupported(spec: ItemSpec): SculkFailure.UnsupportedCapability? {
        val missing = newerComponents(spec).filterNot { it.available }
        if (missing.isEmpty()) return null
        return SculkFailure.UnsupportedCapability(
            "Item data ${missing.joinToString { it.id }}",
            "it needs $NEWER_SINCE or later",
            "Remove ${missing.joinToString { it.property }} from the item, or run $NEWER_SINCE or later.",
        )
    }

    /** The damage types (as keys) and tags (as `#key`) that [spec]'s blocking names and this server has not registered. */
    fun unknownDamageTypes(spec: ItemSpec): List<String> {
        val blocking = spec.blocksAttacks ?: return emptyList()
        val registry = damageTypes()
        val types = blocking.damageReductions.flatMap { it.types }.distinct().filter { registry.get(it) == null }.map { it.asString() }
        val tag = blocking.bypassedBy?.takeUnless { registry.hasTag(RegistryKey.DAMAGE_TYPE.tagKey(it)) }?.let { "#${it.asString()}" }
        return types + listOfNotNull(tag)
    }

    fun create(spec: ItemSpec, text: Text, resolver: TagResolver): ItemStack {
        unsupported(spec)?.let { throw IllegalArgumentException("${it.describe()} ItemSpec.check() reports this as a failure.") }
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
        if (newerComponents(spec).isNotEmpty()) {
            val unknown = unknownDamageTypes(spec)
            require(unknown.isEmpty()) { "no damage type or tag ${unknown.joinToString()} is registered" }
            combat.write(stack, spec)
        }
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
