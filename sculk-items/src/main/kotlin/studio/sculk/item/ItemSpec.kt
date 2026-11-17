package studio.sculk.item

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.pdc.PdcKey
import studio.sculk.pdc.PdcValue
import studio.sculk.text.Text

/** Parts of an item's tooltip a spec can hide. */
@SculkStable
public enum class TooltipPart {
    /** The enchantment lines. */
    ENCHANTMENTS,

    /** The attribute modifier lines, such as attack damage. */
    ATTRIBUTES,

    /** The "Unbreakable" line. */
    UNBREAKABLE,

    /** The "Dyed" line of dyed leather and wolf armour. */
    DYED_COLOR,
}

/** Custom model data as the 1.21.4+ item model definitions read it: lists of each kind. */
@SculkStable
public class CustomModel(
    floats: List<Float> = emptyList(),
    flags: List<Boolean> = emptyList(),
    strings: List<String> = emptyList(),
    colors: List<Color> = emptyList(),
) {
    public val floats: List<Float> = floats.toList()
    public val flags: List<Boolean> = flags.toList()
    public val strings: List<String> = strings.toList()
    public val colors: List<Color> = colors.toList()

    override fun equals(other: Any?): Boolean =
        other is CustomModel && other.floats == floats && other.flags == flags && other.strings == strings && other.colors == colors

    override fun hashCode(): Int = listOf(floats, flags, strings, colors).hashCode()

    override fun toString(): String = "CustomModel(floats=$floats, flags=$flags, strings=$strings, colors=$colors)"
}

/**
 * An item described as data and compiled to data components by [create], the same way on every
 * supported Paper version (on 1.21.4 tooltip parts are hidden through the components' own flags,
 * from 1.21.5 through `TooltipDisplay`). Equal by value, so a spec can be a map key or compared in
 * tests without a server.
 *
 * [name] and [lore] are trusted MiniMessage templates, rendered with the theme of the [Text] passed
 * to [create]; values reach them only as placeholders, from the spec's own [values] or the resolver
 * passed there. Lore lines are not italic unless the template says so.
 *
 * A spec is checked where it is built for everything that needs no server; [check] covers the rest
 * (the material and the enchantments, which need the server's registries).
 */
@SculkStable
public class ItemSpec private constructor(builder: ItemSpecBuilder) {
    public val type: Material = builder.type
    public val amount: Int = builder.amount
    public val name: String? = builder.name
    public val lore: List<String> = builder.lore.toList()
    public val enchantments: Map<Key, Int> = builder.enchantments.toMap()
    public val glint: Boolean? = builder.glint
    public val unbreakable: Boolean = builder.unbreakable
    public val hideTooltip: Boolean = builder.hideTooltip
    public val hidden: Set<TooltipPart> = builder.hidden.toSet()
    public val model: Key? = builder.model
    public val customModel: CustomModel? = builder.customModel
    public val maxStackSize: Int? = builder.maxStackSize
    public val maxDamage: Int? = builder.maxDamage
    public val damage: Int? = builder.damage
    public val rarity: ItemRarity? = builder.rarity
    public val dyedColor: Color? = builder.dyedColor

    /** Marks stacks of this spec as a custom item, see [CustomItems.identify]. */
    public val id: Key? = builder.id
    public val data: List<PdcValue<*>> = builder.data.toList()

    /** Values the templates read as `<key>`, inserted as plain text and never parsed. */
    public val values: Map<String, String> = builder.values.toMap()

    init {
        require(amount in 1..MAX_STACK) { "amount must be 1 to $MAX_STACK, was $amount" }
        name?.let { require(it.isNotBlank()) { "name must not be blank" } }
        require(lore.size <= MAX_LORE) { "an item shows at most $MAX_LORE lore lines, the spec has ${lore.size}" }
        enchantments.forEach { (key, level) ->
            require(level in 1..MAX_ENCHANTMENT_LEVEL) { "enchantment $key level must be 1 to 255, was $level" }
        }
        maxStackSize?.let { require(it in 1..MAX_STACK) { "maxStackSize must be 1 to $MAX_STACK, was $it" } }
        require(amount <= (maxStackSize ?: MAX_STACK)) { "amount $amount exceeds maxStackSize $maxStackSize" }
        maxDamage?.let { require(it >= 1) { "maxDamage must be positive, was $it" } }
        damage?.let { value ->
            require(value >= 0) { "damage must not be negative, was $value" }
            maxDamage?.let { require(value <= it) { "damage $value exceeds maxDamage $it" } }
        }
        require(maxDamage == null || maxStackSize == null || maxStackSize == 1) { "a damageable item cannot stack; maxStackSize must be 1" }
        require(data.map { it.key.key }.distinct().size == data.size) { "each persistent data key may appear once" }
        values.keys.forEach { key -> require(TAG_NAME.matches(key)) { "a value's key is a tag name of a-z, 0-9, '_' and '-', not '$key'" } }
    }

    /**
     * Whether the running server can build this spec: the material is an item and every
     * enchantment is registered. Specs from configuration go through this before [create].
     * Needs a running server.
     */
    public fun check(): SculkResult<ItemSpec> {
        if (!type.isItem) return SculkFailure.InvalidInput("item type", "a material that is an item", type.name)
        val unknown = enchantments.keys.filter { ItemCompiler.enchantment(it) == null }
        if (unknown.isNotEmpty()) return SculkFailure.InvalidInput("enchantments", "registered enchantments", unknown.joinToString())
        return SculkResult.success(this)
    }

    /**
     * A new stack built from this spec, with [name] and [lore] rendered by [text] and [resolver].
     * Any thread; the stack is new and unshared. Throws [IllegalArgumentException] for a spec
     * [check] would reject.
     */
    public fun create(text: Text = Text(), resolver: TagResolver = TagResolver.empty()): ItemStack =
        ItemCompiler.create(this, text, if (values.isEmpty()) resolver else TagResolver.resolver(valueResolver, resolver))

    // The spec's own values come first: they name what this one item shows.
    private val valueResolver: TagResolver by lazy {
        TagResolver.resolver(values.map { (key, value) -> Placeholder.unparsed(key, value) })
    }

    /** A copy with [change] applied on top of this spec. */
    public fun with(change: ItemSpecBuilder.() -> Unit): ItemSpec = ItemSpecBuilder.from(this).apply(change).build()

    // Zero until first computed. A spec never changes once built, so racing threads compute the same
    // value and an int write cannot tear: the String.hashCode idiom, without a lock.
    private var hash: Int = 0

    // Menus compare every slot's spec on each redraw, so this allocates nothing and checks cheap fields first.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemSpec) return false
        if (hash != 0 && other.hash != 0 && hash != other.hash) return false
        return type == other.type &&
            amount == other.amount &&
            unbreakable == other.unbreakable &&
            hideTooltip == other.hideTooltip &&
            glint == other.glint &&
            maxStackSize == other.maxStackSize &&
            maxDamage == other.maxDamage &&
            damage == other.damage &&
            rarity == other.rarity &&
            name == other.name &&
            id == other.id &&
            model == other.model &&
            dyedColor == other.dyedColor &&
            customModel == other.customModel &&
            lore == other.lore &&
            enchantments == other.enchantments &&
            hidden == other.hidden &&
            data == other.data &&
            values == other.values
    }

    override fun hashCode(): Int {
        var result = hash
        if (result == 0) {
            result = type.hashCode()
            result = 31 * result + amount
            result = 31 * result + name.hashCode()
            result = 31 * result + lore.hashCode()
            result = 31 * result + enchantments.hashCode()
            result = 31 * result + glint.hashCode()
            result = 31 * result + unbreakable.hashCode()
            result = 31 * result + hideTooltip.hashCode()
            result = 31 * result + hidden.hashCode()
            result = 31 * result + model.hashCode()
            result = 31 * result + customModel.hashCode()
            result = 31 * result + (maxStackSize ?: 0)
            result = 31 * result + (maxDamage ?: 0)
            result = 31 * result + (damage ?: 0)
            result = 31 * result + rarity.hashCode()
            result = 31 * result + dyedColor.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + data.hashCode()
            result = 31 * result + values.hashCode()
            hash = result
        }
        return result
    }

    override fun toString(): String = "ItemSpec(${type.name}" + (id?.let { ", id=$it" } ?: "") + (name?.let { ", name=$it" } ?: "") + ")"

    @SculkStable
    public companion object {
        /** The largest stack size Minecraft allows for any item. */
        public const val MAX_STACK: Int = 99

        public const val MAX_LORE: Int = 256

        public const val MAX_ENCHANTMENT_LEVEL: Int = 255

        internal fun of(builder: ItemSpecBuilder): ItemSpec = ItemSpec(builder)

        private val TAG_NAME = Regex("[a-z0-9_-]+")
    }
}

/** Marks the item DSL so a nested block cannot reach an outer builder by accident. */
@SculkStable
@DslMarker
public annotation class ItemDsl

/** Builds an [ItemSpec]; see [item]. */
@SculkStable
@ItemDsl
public class ItemSpecBuilder internal constructor(
    /** The material; it must be an item, which [ItemSpec.check] confirms on a running server. */
    public var type: Material,
) {
    /** How many the stack holds, 1 to [ItemSpec.MAX_STACK] and at most [maxStackSize]. */
    public var amount: Int = 1

    /** The item's name, a MiniMessage template; null keeps the material's own name. */
    public var name: String? = null
    internal val lore = mutableListOf<String>()
    internal val enchantments = linkedMapOf<Key, Int>()

    /** Forces the enchantment glint on or off; null leaves it to the enchantments. */
    public var glint: Boolean? = null

    /** Whether the item never takes durability damage. */
    public var unbreakable: Boolean = false

    /** Hides the whole tooltip, name included. */
    public var hideTooltip: Boolean = false
    internal val hidden = linkedSetOf<TooltipPart>()

    /** The item model, by its key in a resource pack; null keeps the material's. */
    public var model: Key? = null

    /** The custom model data item model definitions read; null sets none. */
    public var customModel: CustomModel? = null

    /** How many stack together, 1 to [ItemSpec.MAX_STACK]; null keeps the material's. A damageable item takes 1. */
    public var maxStackSize: Int? = null

    /** The durability, at least 1; null keeps the material's. */
    public var maxDamage: Int? = null

    /** The damage already taken, 0 to [maxDamage]. */
    public var damage: Int? = null

    /** The rarity, which colours the name; null keeps the material's. */
    public var rarity: ItemRarity? = null

    /** The dye colour of dyeable items such as leather armour. */
    public var dyedColor: Color? = null

    /** Marks stacks of this spec as the custom item [id], see [CustomItems]. */
    public var id: Key? = null
    internal val data = mutableListOf<PdcValue<*>>()
    internal val values = linkedMapOf<String, String>()

    /** Appends lore lines, each a MiniMessage template. */
    public fun lore(vararg lines: String) {
        lore += lines
    }

    /** Replaces the lore. */
    public fun lore(lines: List<String>) {
        lore.clear()
        lore += lines
    }

    /** Adds [enchantment] at [level], replacing an earlier level for it. Unrestricted: any level up to 255 on any item. */
    public fun enchant(enchantment: Key, level: Int = 1) {
        enchantments[enchantment] = level
    }

    /** [enchant] by the enchantment itself; reading a registered enchantment needs a running server. */
    public fun enchant(enchantment: Enchantment, level: Int = 1) {
        enchant(enchantment.key, level)
    }

    /** Hides [parts] of the tooltip. */
    public fun hide(vararg parts: TooltipPart) {
        hidden += parts
    }

    /** Stores [value] under [key] in the stack's persistent data. */
    public fun <T : Any> data(key: PdcKey<T>, value: T) {
        data.removeAll { it.key.key == key.key }
        data += PdcValue(key, value)
    }

    /**
     * Gives the templates `<key>` as [value]'s text, unparsed, so a player's name or a price can
     * appear in [name] or [lore] without becoming markup: `name = "<primary><player>"` with
     * `value("player", target.name)`.
     */
    public fun value(key: String, value: Any) {
        values[key] = value.toString()
    }

    internal fun build(): ItemSpec = ItemSpec.of(this)

    internal companion object {
        fun from(spec: ItemSpec): ItemSpecBuilder = ItemSpecBuilder(spec.type).apply {
            amount = spec.amount
            name = spec.name
            lore += spec.lore
            enchantments += spec.enchantments
            glint = spec.glint
            unbreakable = spec.unbreakable
            hideTooltip = spec.hideTooltip
            hidden += spec.hidden
            model = spec.model
            customModel = spec.customModel
            maxStackSize = spec.maxStackSize
            maxDamage = spec.maxDamage
            damage = spec.damage
            rarity = spec.rarity
            dyedColor = spec.dyedColor
            id = spec.id
            data += spec.data
            values += spec.values
        }
    }
}

/**
 * Describes an item:
 *
 * ```kotlin
 * val blade = item(Material.DIAMOND_SWORD) {
 *     name = "<gradient:#7f5af0:#2cb67d>Sculk Blade"
 *     lore("<muted>Hums when the Warden is near.")
 *     enchant(Enchantment.SHARPNESS, 5)
 *     hide(TooltipPart.ENCHANTMENTS)
 *     unbreakable = true
 * }
 * player.inventory.addItem(blade.create())
 * ```
 */
@SculkStable
public fun item(type: Material, build: ItemSpecBuilder.() -> Unit = {}): ItemSpec = ItemSpecBuilder(type).apply(build).build()
