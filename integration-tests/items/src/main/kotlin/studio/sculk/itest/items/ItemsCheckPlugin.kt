package studio.sculk.itest.items

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.registry.set.RegistryKeySet
import io.papermc.paper.registry.tag.Tag
import io.papermc.paper.registry.tag.TagKey
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
import studio.sculk.annotation.SculkExperimental
import studio.sculk.failureOrNull
import studio.sculk.getOrNull
import studio.sculk.getOrThrow
import studio.sculk.item.AttackRange
import studio.sculk.item.BlocksAttacks
import studio.sculk.item.CustomItems
import studio.sculk.item.CustomModel
import studio.sculk.item.ItemSpec
import studio.sculk.item.KineticWeapon
import studio.sculk.item.PiercingWeapon
import studio.sculk.item.SwingAnimation
import studio.sculk.item.TooltipPart
import studio.sculk.item.UseEffects
import studio.sculk.item.Weapon
import studio.sculk.item.item
import studio.sculk.pdc.PdcCodec
import studio.sculk.pdc.PdcKey
import studio.sculk.pdc.read
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Builds item specs on a real server and checks each stack through API that is the same on every
 * Paper version (item meta, enchantment levels, flags), so one oracle covers the 1.21.4 tooltip
 * flags and the 1.21.5+ `TooltipDisplay` alike. Writes `results.properties` and stops the server.
 *
 * The weapon, blocking, reach, swing and use components exist from Paper 1.21.11: there each one is
 * read back by name, as this plugin compiles against 1.21.4, and on 1.21.4 each spec must be refused.
 */
@OptIn(SculkExperimental::class)
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
        verdict("values-are-plain-text") {
            val head = item(Material.PLAYER_HEAD) {
                name = "<primary><player>"
                lore("<muted>Owes <amount> coins")
                value("player", "<red>Evil")
                value("amount", 12.5)
            }.create()
            val shown = head.getData(DataComponentTypes.ITEM_NAME)?.let(plain::serialize)
            val owes = head.lore().orEmpty().map(plain::serialize)
            (shown == "<red>Evil" && owes == listOf("Owes 12.5 coins")) to "name '$shown', lore $owes"
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
        checkCombat()
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

    private fun checkCombat() {
        // The oracle is the version the server reports, not Sculk's own probe.
        val newer = server.minecraftVersion != "1.21.4"
        val generic = Key.key("minecraft:generic")
        val bell = Key.key("minecraft:block.note_block.bell")

        fun combat(name: String, id: String, spec: ItemSpec, read: (ItemStack) -> Pair<Boolean, String>) = verdict(name) {
            if (newer) {
                read(spec.create())
            } else {
                val failure = spec.check().failureOrNull()
                val thrown = runCatching { spec.create() }.exceptionOrNull()
                (failure is SculkFailure.UnsupportedCapability && id in failure.capability && thrown is IllegalArgumentException) to
                    "check gave $failure, create threw $thrown"
            }
        }

        val sword = item(Material.IRON_SWORD) { weapon = Weapon(damagePerAttack = 2, disablesBlockingFor = 5.seconds) }
        combat("weapon", "minecraft:weapon", sword) {
            val weapon = it.component("WEAPON")
            val perAttack = weapon?.property("Weapon", "itemDamagePerAttack")
            val disables = weapon?.property("Weapon", "disableBlockingForSeconds")
            (perAttack == 2 && disables == 5f) to "itemDamagePerAttack $perAttack, disableBlockingForSeconds $disables"
        }

        val shield = BlocksAttacks(
            blockDelay = 250.milliseconds,
            disableCooldownScale = 0.5f,
            damageReductions = listOf(BlocksAttacks.DamageReduction(types = setOf(generic), horizontalBlockingAngle = 60f, factor = 0.5f)),
            itemDamage = BlocksAttacks.ItemDamage(threshold = 3f),
            bypassedBy = Key.key("minecraft:bypasses_shield"),
            blockSound = bell,
        )
        combat("blocks-attacks", "minecraft:blocks_attacks", item(Material.STICK) { blocksAttacks = shield }) {
            val blocking = it.component("BLOCKS_ATTACKS")
            val delay = blocking?.property("BlocksAttacks", "blockDelaySeconds")
            val scale = blocking?.property("BlocksAttacks", "disableCooldownScale")
            val reduction = (blocking?.property("BlocksAttacks", "damageReductions") as? List<*>)?.singleOrNull()
            val angle = reduction?.property("blocksattacks.DamageReduction", "horizontalBlockingAngle")
            val typeSet = reduction?.property("blocksattacks.DamageReduction", "type") as? RegistryKeySet<*>
            val types = typeSet?.values()?.map { key -> key.key() }
            val threshold = blocking?.property("BlocksAttacks", "itemDamage")?.property("blocksattacks.ItemDamageFunction", "threshold")
            // 1.21.11 hands the tag back as a TagKey, 26.1 and later as a Tag.
            val bypass = when (val bypassed = blocking?.property("BlocksAttacks", "bypassedBy")) {
                is TagKey<*> -> bypassed.key()
                is Tag<*> -> bypassed.tagKey().key()
                else -> bypassed
            }
            val sound = blocking?.property("BlocksAttacks", "blockSound")
            val ok = delay == 0.25f && scale == 0.5f && angle == 60f && types == listOf(generic) && threshold == 3f &&
                bypass == Key.key("minecraft:bypasses_shield") && sound == bell
            ok to "delay $delay, scale $scale, angle $angle, types $types, threshold $threshold, bypassedBy $bypass, sound $sound"
        }
        combat("blocks-attacks-checks-damage-types", "minecraft:blocks_attacks", item(Material.STICK) { blocksAttacks = shield }) {
            val nope = Key.key("sculk-itest:nope")
            val unknownType = item(Material.STICK) {
                blocksAttacks = BlocksAttacks(damageReductions = listOf(BlocksAttacks.DamageReduction(types = setOf(nope))))
            }.check()
            val unknownTag = item(Material.STICK) { blocksAttacks = BlocksAttacks(bypassedBy = nope) }.check()
            val known = item(Material.STICK) { blocksAttacks = shield }.check()
            (unknownType is SculkFailure.InvalidInput && unknownTag is SculkFailure.InvalidInput && known.getOrNull() != null) to
                "unknown type $unknownType, unknown tag $unknownTag, known $known"
        }

        val reach = AttackRange(minReach = 1f, maxReach = 6f, maxCreativeReach = 8f, mobFactor = 0.5f)
        combat("attack-range", "minecraft:attack_range", item(Material.IRON_SWORD) { attackRange = reach }) {
            val range = it.component("ATTACK_RANGE")
            val read = listOf("minReach", "maxReach", "minCreativeReach", "maxCreativeReach", "hitboxMargin", "mobFactor")
                .map { name -> range?.property("AttackRange", name) }
            (read == listOf(1f, 6f, 0f, 8f, 0.3f, 0.5f)) to "reaches, margin and mob factor $read"
        }

        val stab = SwingAnimation(SwingAnimation.Style.STAB, durationTicks = 10)
        combat("swing-animation", "minecraft:swing_animation", item(Material.IRON_SWORD) { swingAnimation = stab }) {
            // Renamed ATTACK_ANIMATION in 26.3.
            val swing = it.component("ATTACK_ANIMATION", "SWING_ANIMATION")
            val style = (swing?.property("SwingAnimation", "type") as? Enum<*>)?.name
            val duration = swing?.property("SwingAnimation", "duration")
            (style == "STAB" && duration == 10) to "type $style, duration $duration"
        }

        val bow = item(Material.BOW) { useEffects = UseEffects(canSprint = true, speedMultiplier = 1f) }
        combat("use-effects", "minecraft:use_effects", bow) {
            val effects = it.component("USE_EFFECTS")
            val sprint = effects?.property("UseEffects", "canSprint")
            val vibrations = effects?.property("UseEffects", "interactVibrations")
            val speed = effects?.property("UseEffects", "speedMultiplier")
            (sprint == true && vibrations == true && speed == 1f) to
                "canSprint $sprint, interactVibrations $vibrations, speedMultiplier $speed"
        }

        val jab = PiercingWeapon(dealsKnockback = false, dismounts = true, hitSound = bell)
        combat("piercing-weapon", "minecraft:piercing_weapon", item(Material.IRON_SWORD) { piercingWeapon = jab }) {
            val piercing = it.component("PIERCING_WEAPON")
            val knockback = piercing?.property("PiercingWeapon", "dealsKnockback")
            val dismounts = piercing?.property("PiercingWeapon", "dismounts")
            val hit = piercing?.property("PiercingWeapon", "hitSound")
            (knockback == false && dismounts == true && hit == bell) to "dealsKnockback $knockback, dismounts $dismounts, hitSound $hit"
        }

        val charge = KineticWeapon(
            contactCooldownTicks = 20,
            delayTicks = 5,
            damageConditions = KineticWeapon.Condition(maxDurationTicks = 80, minSpeed = 0.5f),
            damageMultiplier = 1.5f,
        )
        combat("kinetic-weapon", "minecraft:kinetic_weapon", item(Material.IRON_SWORD) { kineticWeapon = charge }) {
            val kinetic = it.component("KINETIC_WEAPON")
            val cooldown = kinetic?.property("KineticWeapon", "contactCooldownTicks")
            val delay = kinetic?.property("KineticWeapon", "delayTicks")
            val multiplier = kinetic?.property("KineticWeapon", "damageMultiplier")
            val condition = kinetic?.property("KineticWeapon", "damageConditions")
            val duration = condition?.property("KineticWeapon\$Condition", "maxDurationTicks")
            val speed = condition?.property("KineticWeapon\$Condition", "minSpeed")
            val dismount = kinetic?.property("KineticWeapon", "dismountConditions")
            (cooldown == 20 && delay == 5 && multiplier == 1.5f && duration == 80 && speed == 0.5f && dismount == null) to
                "contactCooldownTicks $cooldown, delayTicks $delay, damageMultiplier $multiplier, " +
                    "damage for $duration at $speed, dismount $dismount"
        }

        combat("minimum-attack-charge", "minecraft:minimum_attack_charge", item(Material.IRON_SWORD) { minimumAttackCharge = 0.9f }) {
            val charged = it.component("MINIMUM_ATTACK_CHARGE")
            (charged == 0.9f) to "minimum attack charge $charged"
        }

        verdict("combat-specs-from-configuration") {
            val scope = ResourceScope.root("combat-check")
            val items = CustomItems()
            items.define(
                scope,
                item(Material.SHIELD) {
                    id = Key.key("sculk-itest:buckler")
                    blocksAttacks = shield
                },
            )
            val made = items.create(Key.key("sculk-itest:buckler"))
            scope.close()
            val ok = if (newer) made.getOrNull()?.component("BLOCKS_ATTACKS") != null else made is SculkFailure.UnsupportedCapability
            ok to "on ${server.minecraftVersion} CustomItems.create gave $made"
        }
    }

    /** The value of the first of the components [fields] Paper declares, found by name as this plugin compiles against 1.21.4. */
    private fun ItemStack.component(vararg fields: String): Any? {
        val types = DataComponentTypes::class.java
        val field = fields.firstNotNullOfOrNull { name -> runCatching { types.getField(name) }.getOrNull() }
            ?: error("this server has none of ${fields.joinToString()}")
        @Suppress("UNCHECKED_CAST")
        val type = field.get(null) as DataComponentType.Valued<Any>
        return getData(type)
    }

    /** Calls the accessor [name] of the Paper component interface [type], named relative to `datacomponent.item`, on this value. */
    private fun Any.property(type: String, name: String): Any? =
        Class.forName("io.papermc.paper.datacomponent.item.$type").getMethod(name).invoke(this)

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
