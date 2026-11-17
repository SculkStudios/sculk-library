package studio.sculk.item.adapter

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.set.RegistrySet
import io.papermc.paper.registry.tag.TagKey
import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import studio.sculk.annotation.SculkExperimental
import studio.sculk.item.AttackRange
import studio.sculk.item.BlocksAttacks
import studio.sculk.item.CombatAdapter
import studio.sculk.item.ItemSpec
import studio.sculk.item.KineticWeapon
import studio.sculk.item.PiercingWeapon
import studio.sculk.item.SwingAnimation
import studio.sculk.item.UseEffects
import studio.sculk.item.Weapon
import java.lang.reflect.Method
import kotlin.time.Duration
import kotlin.time.DurationUnit
import io.papermc.paper.datacomponent.item.AttackRange as PaperAttackRange
import io.papermc.paper.datacomponent.item.BlocksAttacks as PaperBlocksAttacks
import io.papermc.paper.datacomponent.item.KineticWeapon as PaperKineticWeapon
import io.papermc.paper.datacomponent.item.PiercingWeapon as PaperPiercingWeapon
import io.papermc.paper.datacomponent.item.SwingAnimation as PaperSwingAnimation
import io.papermc.paper.datacomponent.item.UseEffects as PaperUseEffects
import io.papermc.paper.datacomponent.item.Weapon as PaperWeapon

/**
 * Paper 1.21.11 and later: the weapon, blocking, reach, swing and use components. Instantiated by
 * name only, once a spec that sets one of them has passed the probe for every component it sets.
 */
@OptIn(SculkExperimental::class)
internal class CombatComponentAdapter : CombatAdapter {
    // 26.3 renamed SWING_ANIMATION to ATTACK_ANIMATION with the same value type; this source set
    // compiles against 26.3, so the older name is reachable only by reflection.
    private val swingAnimationType: DataComponentType.Valued<PaperSwingAnimation> by lazy {
        val types = DataComponentTypes::class.java
        val field = runCatching { types.getField("ATTACK_ANIMATION") }.getOrElse { types.getField("SWING_ANIMATION") }

        @Suppress("UNCHECKED_CAST")
        val type = field.get(null) as DataComponentType.Valued<PaperSwingAnimation>
        type
    }

    // Before 26.1 the builder takes the tag as a TagKey; from 26.1 as a RegistryKeySet, which a
    // registered tag is. Null where only the newer signature exists.
    private val tagKeyBypass: Method? by lazy {
        runCatching { PaperBlocksAttacks.Builder::class.java.getMethod("bypassedBy", TagKey::class.java) }.getOrNull()
    }

    override fun write(stack: ItemStack, spec: ItemSpec) {
        spec.weapon?.let { stack.setData(DataComponentTypes.WEAPON, weapon(it)) }
        spec.blocksAttacks?.let { stack.setData(DataComponentTypes.BLOCKS_ATTACKS, blocksAttacks(it)) }
        spec.attackRange?.let { stack.setData(DataComponentTypes.ATTACK_RANGE, attackRange(it)) }
        spec.swingAnimation?.let { stack.setData(swingAnimationType, swingAnimation(it)) }
        spec.useEffects?.let { stack.setData(DataComponentTypes.USE_EFFECTS, useEffects(it)) }
        spec.piercingWeapon?.let { stack.setData(DataComponentTypes.PIERCING_WEAPON, piercingWeapon(it)) }
        spec.kineticWeapon?.let { stack.setData(DataComponentTypes.KINETIC_WEAPON, kineticWeapon(it)) }
        spec.minimumAttackCharge?.let { stack.setData(DataComponentTypes.MINIMUM_ATTACK_CHARGE, it) }
    }

    private fun weapon(weapon: Weapon): PaperWeapon = PaperWeapon.weapon()
        .itemDamagePerAttack(weapon.damagePerAttack)
        .disableBlockingForSeconds(weapon.disablesBlockingFor.inSeconds())
        .build()

    private fun blocksAttacks(blocking: BlocksAttacks): PaperBlocksAttacks {
        val itemDamage = ItemDamageFunction.itemDamageFunction()
            .threshold(blocking.itemDamage.threshold)
            .base(blocking.itemDamage.base)
            .factor(blocking.itemDamage.factor)
            .build()
        val builder = PaperBlocksAttacks.blocksAttacks()
            .blockDelaySeconds(blocking.blockDelay.inSeconds())
            .disableCooldownScale(blocking.disableCooldownScale)
            .damageReductions(blocking.damageReductions.map(::damageReduction))
            .itemDamage(itemDamage)
        blocking.bypassedBy?.let { builder.bypass(it) }
        blocking.blockSound?.let { builder.blockSound(it) }
        blocking.disableSound?.let { builder.disableSound(it) }
        return builder.build()
    }

    private fun damageReduction(reduction: BlocksAttacks.DamageReduction): DamageReduction {
        val builder = DamageReduction.damageReduction()
            .horizontalBlockingAngle(reduction.horizontalBlockingAngle)
            .base(reduction.base)
            .factor(reduction.factor)
        if (reduction.types.isNotEmpty()) {
            builder.type(RegistrySet.keySet(RegistryKey.DAMAGE_TYPE, reduction.types.map { RegistryKey.DAMAGE_TYPE.typedKey(it) }))
        }
        return builder.build()
    }

    private fun PaperBlocksAttacks.Builder.bypass(tag: Key) {
        val key = RegistryKey.DAMAGE_TYPE.tagKey(tag)
        val legacy = tagKeyBypass
        if (legacy != null) {
            legacy.invoke(this, key)
        } else {
            bypassedBy(RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(key))
        }
    }

    private fun attackRange(range: AttackRange): PaperAttackRange = PaperAttackRange.attackRange()
        .minReach(range.minReach)
        .maxReach(range.maxReach)
        .minCreativeReach(range.minCreativeReach)
        .maxCreativeReach(range.maxCreativeReach)
        .hitboxMargin(range.hitboxMargin)
        .mobFactor(range.mobFactor)
        .build()

    private fun swingAnimation(swing: SwingAnimation): PaperSwingAnimation {
        val style = when (swing.style) {
            SwingAnimation.Style.NONE -> PaperSwingAnimation.Animation.NONE
            SwingAnimation.Style.WHACK -> PaperSwingAnimation.Animation.WHACK
            SwingAnimation.Style.STAB -> PaperSwingAnimation.Animation.STAB
        }
        return PaperSwingAnimation.swingAnimation().type(style).duration(swing.durationTicks).build()
    }

    private fun useEffects(effects: UseEffects): PaperUseEffects = PaperUseEffects.useEffects()
        .canSprint(effects.canSprint)
        .interactVibrations(effects.interactVibrations)
        .speedMultiplier(effects.speedMultiplier)
        .build()

    private fun piercingWeapon(piercing: PiercingWeapon): PaperPiercingWeapon {
        val builder = PaperPiercingWeapon.piercingWeapon()
            .dealsKnockback(piercing.dealsKnockback)
            .dismounts(piercing.dismounts)
        piercing.sound?.let { builder.sound(it) }
        piercing.hitSound?.let { builder.hitSound(it) }
        return builder.build()
    }

    private fun kineticWeapon(kinetic: KineticWeapon): PaperKineticWeapon {
        val builder = PaperKineticWeapon.kineticWeapon()
            .contactCooldownTicks(kinetic.contactCooldownTicks)
            .delayTicks(kinetic.delayTicks)
            .forwardMovement(kinetic.forwardMovement)
            .damageMultiplier(kinetic.damageMultiplier)
        kinetic.dismountConditions?.let { builder.dismountConditions(condition(it)) }
        kinetic.knockbackConditions?.let { builder.knockbackConditions(condition(it)) }
        kinetic.damageConditions?.let { builder.damageConditions(condition(it)) }
        kinetic.sound?.let { builder.sound(it) }
        kinetic.hitSound?.let { builder.hitSound(it) }
        return builder.build()
    }

    private fun condition(condition: KineticWeapon.Condition): PaperKineticWeapon.Condition =
        PaperKineticWeapon.condition(condition.maxDurationTicks, condition.minSpeed, condition.minRelativeSpeed)

    private fun Duration.inSeconds(): Float = toDouble(DurationUnit.SECONDS).toFloat()
}
