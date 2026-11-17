package studio.sculk.itest.items

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.ResourceScope
import studio.sculk.SculkFailure
import studio.sculk.getOrNull
import studio.sculk.getOrThrow
import studio.sculk.item.CustomItems
import studio.sculk.item.CustomModel
import studio.sculk.item.TooltipPart
import studio.sculk.item.item
import studio.sculk.pdc.PdcCodec
import studio.sculk.pdc.PdcKey
import studio.sculk.pdc.read
import java.io.File
import java.util.UUID

/**
 * Builds item specs on a real server and checks each stack through API that is the same on every
 * Paper version (item meta, enchantment levels, flags), so one oracle covers the 1.21.4 tooltip
 * flags and the 1.21.5+ `TooltipDisplay` alike. Writes `results.properties` and stops the server.
 */
class ItemsCheckPlugin : JavaPlugin() {
    private val results = sortedMapOf<String, String>()
    private val plain = PlainTextComponentSerializer.plainText()
    private val owner = PdcKey(NamespacedKey("sculk-itest", "owner"), PdcCodec.UUID)
    private val tags = PdcKey(NamespacedKey("sculk-itest", "tags"), PdcCodec.STRING.list())

    private val blade = item(Material.DIAMOND_SWORD) {
        name = "<primary>Sculk Blade"
        lore("<muted>Hums when the Warden is near.", "<italic>Whispers")
        enchant(Enchantment.SHARPNESS, 5)
        glint = false
        unbreakable = true
        hide(TooltipPart.ENCHANTMENTS, TooltipPart.UNBREAKABLE, TooltipPart.ATTRIBUTES)
        model = Key.key("sculk-itest:blade")
        customModel = CustomModel(floats = listOf(7f), strings = listOf("warden"))
        id = Key.key("sculk-itest:blade")
    }

    override fun onEnable() {
        server.globalRegionScheduler.run(this) { check() }
    }

    private fun check() {
        val stack = blade.create()
        val meta = stack.itemMeta
        verdict("name-and-lore") {
            val name = stack.getData(DataComponentTypes.ITEM_NAME)?.let(plain::serialize)
            val lore = stack.lore().orEmpty()
            val ok = name == "Sculk Blade" &&
                lore.map(plain::serialize) == listOf("Hums when the Warden is near.", "Whispers") &&
                !lore[0].italicAt("Hums") &&
                lore[1].italicAt("Whispers")
            ok to "name '$name', lore ${lore.map { plain.serialize(it) }}, italic ${lore[0].italicAt("Hums")} ${lore[1].italicAt("Whispers")}"
        }
        verdict("enchantments-and-glint") {
            val level = stack.getEnchantmentLevel(Enchantment.SHARPNESS)
            val glint = stack.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)
            (level == 5 && glint == false) to "sharpness $level, glint override $glint"
        }
        verdict("unbreakable") { meta.isUnbreakable to "unbreakable ${meta.isUnbreakable}" }
        verdict("hidden-parts") {
            val flags = meta.itemFlags
            val wanted = setOf(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES)
            (flags.containsAll(wanted) && !meta.isHideTooltip) to "flags $flags, whole tooltip hidden ${meta.isHideTooltip}"
        }
        verdict("hidden-attributes-keep-their-effect") {
            val modifiers = stack.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)?.modifiers().orEmpty()
            val damage = modifiers.any { it.attribute() == Attribute.ATTACK_DAMAGE }
            // On 1.21.5+ TooltipDisplay hides the type's default modifiers without copying them.
            val implicit = modifiers.isEmpty() && stack.type.getDefaultAttributeModifiers().containsKey(Attribute.ATTACK_DAMAGE)
            (damage || implicit) to "modifiers ${modifiers.map { it.attribute() }}"
        }
        verdict("model-and-custom-model-data") {
            val model = stack.getData(DataComponentTypes.ITEM_MODEL)
            val data = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA)
            (model == Key.key("sculk-itest:blade") && data?.floats() == listOf(7f) && data.strings() == listOf("warden")) to
                "model $model, floats ${data?.floats()}, strings ${data?.strings()}"
        }
        verdict("whole-tooltip-hides") {
            val hidden = item(Material.PAPER) { hideTooltip = true }.create().itemMeta.isHideTooltip
            hidden to "isHideTooltip $hidden"
        }
        verdict("dyed-colour") {
            val helmet = item(Material.LEATHER_HELMET) {
                dyedColor = Color.fromRGB(0x2cb67d)
                hide(TooltipPart.DYED_COLOR)
            }.create()
            val helmetMeta = helmet.itemMeta as LeatherArmorMeta
            (helmetMeta.color == Color.fromRGB(0x2cb67d) && helmetMeta.hasItemFlag(ItemFlag.HIDE_DYE)) to
                "colour ${helmetMeta.color}, flags ${helmetMeta.itemFlags}"
        }
        verdict("persistent-data-survives-bytes") {
            val id = UUID.randomUUID()
            val tagged = item(Material.EMERALD) {
                data(owner, id)
                data(tags, listOf("rare", "shop"))
            }.create()
            val copy = ItemStack.deserializeBytes(tagged.serializeAsBytes())
            (copy.read(owner).getOrNull() == id && copy.read(tags).getOrNull() == listOf("rare", "shop")) to
                "owner ${copy.read(owner)}, tags ${copy.read(tags)}"
        }
        verdict("custom-items") {
            val scope = ResourceScope.root("items-check")
            val items = CustomItems()
            items.define(scope, blade)
            val made = items.create(Key.key("sculk-itest:blade"), 1).getOrThrow()
            val identified = items.identify(made)
            val plainStone = items.identify(ItemStack.of(Material.STONE))
            val unknown = items.create(Key.key("sculk-itest:nothing"))
            scope.close()
            val gone = items.spec(Key.key("sculk-itest:blade"))
            (identified == Key.key("sculk-itest:blade") && plainStone == null && unknown is SculkFailure && gone == null) to
                "identified $identified, stone $plainStone, unknown $unknown, after close $gone"
        }
        verdict("check-rejects-what-the-server-lacks") {
            val badEnchant = item(Material.STONE) { enchant(Key.key("sculk-itest:nope"), 1) }.check()
            val notAnItem = item(Material.WATER).check()
            (badEnchant is SculkFailure && notAnItem is SculkFailure && blade.check().getOrNull() == blade) to
                "unknown enchantment $badEnchant, water $notAnItem"
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.shutdown()
    }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }

    /** Whether the text starting with [prefix] shows in italics: the nearest explicit setting wins, as on the client. */
    private fun Component.italicAt(prefix: String, inherited: Boolean = true): Boolean {
        val here = when (decoration(TextDecoration.ITALIC)) {
            TextDecoration.State.TRUE -> true
            TextDecoration.State.FALSE -> false
            else -> inherited
        }
        if (this is net.kyori.adventure.text.TextComponent && content().startsWith(prefix)) return here
        return children().firstNotNullOfOrNull { child -> if (plain.serialize(child).contains(prefix)) child.italicAt(prefix, here) else null } ?: here
    }
}
