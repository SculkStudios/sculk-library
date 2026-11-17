package studio.sculk.item

import net.kyori.adventure.key.Key
import studio.sculk.annotation.SculkExperimental
import kotlin.time.Duration

/**
 * How the item works as a weapon (`minecraft:weapon`). Needs Paper 1.21.11 or later, as do
 * [BlocksAttacks], [AttackRange], [SwingAnimation], [UseEffects], [PiercingWeapon] and
 * [KineticWeapon]: on an older server [ItemSpec.check] fails with an `UnsupportedCapability`.
 */
@SculkExperimental
public class Weapon(
    /** Durability each attack costs the item, 0 or more. */
    public val damagePerAttack: Int = 1,
    /** How long a hit keeps the target's shield, or any item that blocks, from blocking; zero for not at all. */
    public val disablesBlockingFor: Duration = Duration.ZERO,
) {
    init {
        require(damagePerAttack >= 0) { "damagePerAttack must not be negative, was $damagePerAttack" }
        requireSeconds("disablesBlockingFor", disablesBlockingFor)
    }

    override fun equals(other: Any?): Boolean =
        other is Weapon && other.damagePerAttack == damagePerAttack && other.disablesBlockingFor == disablesBlockingFor

    override fun hashCode(): Int = 31 * damagePerAttack + disablesBlockingFor.hashCode()

    override fun toString(): String = "Weapon(damagePerAttack=$damagePerAttack, disablesBlockingFor=$disablesBlockingFor)"
}

/**
 * Lets the item block attacks while it is used, as a shield does (`minecraft:blocks_attacks`). The
 * defaults block all of a hit from within 90 degrees of the holder's facing and cost the item the
 * hit's damage, rounded down, in durability; a shield-like item usually also sets [bypassedBy] to
 * `minecraft:bypasses_shield`.
 */
@SculkExperimental
public class BlocksAttacks(
    /** How long the item must be in use before it blocks. */
    public val blockDelay: Duration = Duration.ZERO,
    /** Scales how long an attack that disables blocking (an axe, a [Weapon.disablesBlockingFor]) disables this item; 0 for immune. */
    public val disableCooldownScale: Float = 1f,
    damageReductions: List<DamageReduction> = listOf(DamageReduction()),
    /** The durability a blocked hit costs. */
    public val itemDamage: ItemDamage = ItemDamage(),
    /** A damage type tag whose damage is never blocked, such as `minecraft:bypasses_shield`; null for none. */
    public val bypassedBy: Key? = null,
    /** The sound event played on a successful block; null for none. */
    public val blockSound: Key? = null,
    /** The sound event played when an attack disables the item; null for none. */
    public val disableSound: Key? = null,
) {
    /** How much of each hit is blocked: the reductions that apply add up, to at most the whole hit; empty blocks nothing. */
    public val damageReductions: List<DamageReduction> = damageReductions.toList()

    init {
        requireSeconds("blockDelay", blockDelay)
        require(disableCooldownScale.isFinite() && disableCooldownScale >= 0f) {
            "disableCooldownScale must be 0 or more, was $disableCooldownScale"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BlocksAttacks &&
            other.blockDelay == blockDelay &&
            sameFloat(other.disableCooldownScale, disableCooldownScale) &&
            other.damageReductions == damageReductions &&
            other.itemDamage == itemDamage &&
            other.bypassedBy == bypassedBy &&
            other.blockSound == blockSound &&
            other.disableSound == disableSound
    }

    override fun hashCode(): Int =
        listOf(blockDelay, disableCooldownScale, damageReductions, itemDamage, bypassedBy, blockSound, disableSound).hashCode()

    override fun toString(): String =
        "BlocksAttacks(blockDelay=$blockDelay, disableCooldownScale=$disableCooldownScale, damageReductions=$damageReductions, " +
            "itemDamage=$itemDamage, bypassedBy=$bypassedBy, blockSound=$blockSound, disableSound=$disableSound)"

    /**
     * Blocks `base + factor * damage` of a hit, at most all of it, when the hit comes from within
     * [horizontalBlockingAngle] degrees of where the holder faces and is one of [types].
     */
    @SculkExperimental
    public class DamageReduction(
        types: Set<Key> = emptySet(),
        /** The widest angle, in degrees and above 0, between the holder's facing and the attack that is still blocked. */
        public val horizontalBlockingAngle: Float = 90f,
        /** The damage blocked whatever the hit's size. */
        public val base: Float = 0f,
        /** The share of the hit's damage blocked on top of [base]. */
        public val factor: Float = 1f,
    ) {
        /** The damage types this reduction applies to, by key; empty for every type. */
        public val types: Set<Key> = types.toSet()

        init {
            require(horizontalBlockingAngle.isFinite() && horizontalBlockingAngle > 0f) {
                "horizontalBlockingAngle must be above 0, was $horizontalBlockingAngle"
            }
            require(base.isFinite() && factor.isFinite()) { "base and factor must be finite, were $base and $factor" }
        }

        override fun equals(other: Any?): Boolean = other is DamageReduction &&
            other.types == types &&
            sameFloat(other.horizontalBlockingAngle, horizontalBlockingAngle) &&
            sameFloat(other.base, base) &&
            sameFloat(other.factor, factor)

        override fun hashCode(): Int = listOf(types, horizontalBlockingAngle, base, factor).hashCode()

        override fun toString(): String =
            "DamageReduction(types=$types, horizontalBlockingAngle=$horizontalBlockingAngle, base=$base, factor=$factor)"
    }

    /** A blocked hit of [threshold] damage or more costs `floor(base + factor * damage)` durability; a smaller one costs none. */
    @SculkExperimental
    public class ItemDamage(
        /** The least damage a blocked hit must deal to cost durability, 0 or more. */
        public val threshold: Float = 1f,
        /** The durability every costing hit takes. */
        public val base: Float = 0f,
        /** The share of the hit's damage taken as durability on top of [base]. */
        public val factor: Float = 1f,
    ) {
        init {
            require(threshold.isFinite() && threshold >= 0f) { "threshold must be 0 or more, was $threshold" }
            require(base.isFinite() && factor.isFinite()) { "base and factor must be finite, were $base and $factor" }
        }

        override fun equals(other: Any?): Boolean =
            other is ItemDamage && sameFloat(other.threshold, threshold) && sameFloat(other.base, base) && sameFloat(other.factor, factor)

        override fun hashCode(): Int = listOf(threshold, base, factor).hashCode()

        override fun toString(): String = "ItemDamage(threshold=$threshold, base=$base, factor=$factor)"
    }
}

/**
 * How far attacks with the item reach, in blocks (`minecraft:attack_range`). An attack lands on a
 * target between the minimum and maximum reach; creative players use the creative pair.
 */
@SculkExperimental
public class AttackRange(
    /** The closest a target may be, 0 to [MAX_REACH] and at most [maxReach]. */
    public val minReach: Float = 0f,
    /** The farthest a target may be, 0 to [MAX_REACH]. */
    public val maxReach: Float = 3f,
    /** [minReach] for players in creative mode. */
    public val minCreativeReach: Float = 0f,
    /** [maxReach] for players in creative mode. */
    public val maxCreativeReach: Float = 5f,
    /** How much each target's hitbox grows for the reach check, 0 to 1. */
    public val hitboxMargin: Float = 0.3f,
    /** Scales both reaches when a mob holds the item, 0 to 2. */
    public val mobFactor: Float = 1f,
) {
    init {
        val reaches = mapOf(
            "minReach" to minReach,
            "maxReach" to maxReach,
            "minCreativeReach" to minCreativeReach,
            "maxCreativeReach" to maxCreativeReach,
        )
        reaches.forEach { (name, reach) -> require(reach in 0f..MAX_REACH) { "$name must be 0 to $MAX_REACH, was $reach" } }
        require(minReach <= maxReach) { "minReach $minReach exceeds maxReach $maxReach" }
        require(minCreativeReach <= maxCreativeReach) { "minCreativeReach $minCreativeReach exceeds maxCreativeReach $maxCreativeReach" }
        require(hitboxMargin in 0f..1f) { "hitboxMargin must be 0 to 1, was $hitboxMargin" }
        require(mobFactor in 0f..2f) { "mobFactor must be 0 to 2, was $mobFactor" }
    }

    override fun equals(other: Any?): Boolean = other is AttackRange &&
        sameFloat(other.minReach, minReach) &&
        sameFloat(other.maxReach, maxReach) &&
        sameFloat(other.minCreativeReach, minCreativeReach) &&
        sameFloat(other.maxCreativeReach, maxCreativeReach) &&
        sameFloat(other.hitboxMargin, hitboxMargin) &&
        sameFloat(other.mobFactor, mobFactor)

    override fun hashCode(): Int = listOf(minReach, maxReach, minCreativeReach, maxCreativeReach, hitboxMargin, mobFactor).hashCode()

    override fun toString(): String = "AttackRange(minReach=$minReach, maxReach=$maxReach, minCreativeReach=$minCreativeReach, " +
        "maxCreativeReach=$maxCreativeReach, hitboxMargin=$hitboxMargin, mobFactor=$mobFactor)"

    @SculkExperimental
    public companion object {
        /** The longest reach Minecraft accepts, in blocks. */
        public const val MAX_REACH: Float = 64f
    }
}

/**
 * The arm animation of an attack with the item (`minecraft:swing_animation`; from Minecraft 26.3
 * `minecraft:attack_animation`, which Sculk writes there instead).
 */
@SculkExperimental
public class SwingAnimation(
    /** The kind of swing. */
    public val style: Style = Style.WHACK,
    /** How long the swing lasts, in ticks, 1 or more. */
    public val durationTicks: Int = 6,
) {
    init {
        require(durationTicks >= 1) { "durationTicks must be 1 or more, was $durationTicks" }
    }

    /** The kinds of swing Minecraft draws. */
    @SculkExperimental
    public enum class Style {
        /** No swing at all. */
        NONE,

        /** The sweep of a sword or an axe. */
        WHACK,

        /** The forward thrust of a spear. */
        STAB,
    }

    override fun equals(other: Any?): Boolean = other is SwingAnimation && other.style == style && other.durationTicks == durationTicks

    override fun hashCode(): Int = 31 * style.hashCode() + durationTicks

    override fun toString(): String = "SwingAnimation(style=$style, durationTicks=$durationTicks)"
}

/** What using the item, such as drawing a bow or eating, does to its holder (`minecraft:use_effects`). */
@SculkExperimental
public class UseEffects(
    /** Whether the holder can sprint while using it. */
    public val canSprint: Boolean = false,
    /** Whether using it sets off vibrations that sculk sensors and the Warden notice. */
    public val interactVibrations: Boolean = true,
    /** The holder's movement speed while using it, as a share of normal speed, 0 to 1. */
    public val speedMultiplier: Float = 0.2f,
) {
    init {
        require(speedMultiplier in 0f..1f) { "speedMultiplier must be 0 to 1, was $speedMultiplier" }
    }

    override fun equals(other: Any?): Boolean = other is UseEffects &&
        other.canSprint == canSprint &&
        other.interactVibrations == interactVibrations &&
        sameFloat(other.speedMultiplier, speedMultiplier)

    override fun hashCode(): Int = listOf(canSprint, interactVibrations, speedMultiplier).hashCode()

    override fun toString(): String =
        "UseEffects(canSprint=$canSprint, interactVibrations=$interactVibrations, speedMultiplier=$speedMultiplier)"
}

/** Makes an attack hit every entity along its reach rather than the first, as a spear's jab does (`minecraft:piercing_weapon`). */
@SculkExperimental
public class PiercingWeapon(
    /** Whether the hit knocks targets back. */
    public val dealsKnockback: Boolean = true,
    /** Whether the hit throws targets off what they ride. */
    public val dismounts: Boolean = false,
    /** The sound event played on the attack; null for none. */
    public val sound: Key? = null,
    /** The sound event played on each entity hit; null for none. */
    public val hitSound: Key? = null,
) {
    override fun equals(other: Any?): Boolean = other is PiercingWeapon &&
        other.dealsKnockback == dealsKnockback &&
        other.dismounts == dismounts &&
        other.sound == sound &&
        other.hitSound == hitSound

    override fun hashCode(): Int = listOf(dealsKnockback, dismounts, sound, hitSound).hashCode()

    override fun toString(): String =
        "PiercingWeapon(dealsKnockback=$dealsKnockback, dismounts=$dismounts, sound=$sound, hitSound=$hitSound)"
}

/**
 * A charged attack made by holding the item out while moving, as a spear's charge is
 * (`minecraft:kinetic_weapon`). Each field mirrors the component's field of the same name; damage
 * grows with the holder's speed.
 */
@SculkExperimental
public class KineticWeapon(
    /** Ticks before the charge can hit the same entity again, 0 or more. */
    public val contactCooldownTicks: Int = 10,
    /** Ticks from starting to use the item until the charge begins, 0 or more. */
    public val delayTicks: Int = 0,
    /** When the charge throws a target off its mount; null for never. */
    public val dismountConditions: Condition? = null,
    /** When the charge knocks a target back; null for never. */
    public val knockbackConditions: Condition? = null,
    /** When the charge damages a target; null for never. */
    public val damageConditions: Condition? = null,
    /** Minecraft's `forward_movement`: how far ahead of the holder the charge reaches. */
    public val forwardMovement: Float = 0f,
    /** Scales the damage the holder's speed adds. */
    public val damageMultiplier: Float = 1f,
    /** The sound event played when the charge begins; null for none. */
    public val sound: Key? = null,
    /** The sound event played on each entity hit; null for none. */
    public val hitSound: Key? = null,
) {
    init {
        require(contactCooldownTicks >= 0) { "contactCooldownTicks must not be negative, was $contactCooldownTicks" }
        require(delayTicks >= 0) { "delayTicks must not be negative, was $delayTicks" }
        require(forwardMovement.isFinite() && damageMultiplier.isFinite()) {
            "forwardMovement and damageMultiplier must be finite, were $forwardMovement and $damageMultiplier"
        }
    }

    /**
     * One effect of the charge applies for [maxDurationTicks] after the charge begins, while the holder
     * moves at [minSpeed] or more and at [minRelativeSpeed] or more relative to the target.
     */
    @SculkExperimental
    public class Condition(
        /** How long after the charge begins the effect can apply, in ticks, 0 or more. */
        public val maxDurationTicks: Int,
        /** The holder's least speed along its facing. */
        public val minSpeed: Float = 0f,
        /** The holder's least speed relative to the target. */
        public val minRelativeSpeed: Float = 0f,
    ) {
        init {
            require(maxDurationTicks >= 0) { "maxDurationTicks must not be negative, was $maxDurationTicks" }
            require(minSpeed.isFinite() && minRelativeSpeed.isFinite()) { "speeds must be finite, were $minSpeed and $minRelativeSpeed" }
        }

        override fun equals(other: Any?): Boolean = other is Condition &&
            other.maxDurationTicks == maxDurationTicks &&
            sameFloat(other.minSpeed, minSpeed) &&
            sameFloat(other.minRelativeSpeed, minRelativeSpeed)

        override fun hashCode(): Int = listOf(maxDurationTicks, minSpeed, minRelativeSpeed).hashCode()

        override fun toString(): String =
            "Condition(maxDurationTicks=$maxDurationTicks, minSpeed=$minSpeed, minRelativeSpeed=$minRelativeSpeed)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KineticWeapon &&
            other.contactCooldownTicks == contactCooldownTicks &&
            other.delayTicks == delayTicks &&
            other.dismountConditions == dismountConditions &&
            other.knockbackConditions == knockbackConditions &&
            other.damageConditions == damageConditions &&
            sameFloat(other.forwardMovement, forwardMovement) &&
            sameFloat(other.damageMultiplier, damageMultiplier) &&
            other.sound == sound &&
            other.hitSound == hitSound
    }

    override fun hashCode(): Int = listOf(
        contactCooldownTicks,
        delayTicks,
        dismountConditions,
        knockbackConditions,
        damageConditions,
        forwardMovement,
        damageMultiplier,
        sound,
        hitSound,
    ).hashCode()

    override fun toString(): String =
        "KineticWeapon(contactCooldownTicks=$contactCooldownTicks, delayTicks=$delayTicks, dismountConditions=$dismountConditions, " +
            "knockbackConditions=$knockbackConditions, damageConditions=$damageConditions, forwardMovement=$forwardMovement, " +
            "damageMultiplier=$damageMultiplier, sound=$sound, hitSound=$hitSound)"
}

// Bitwise, like Float.equals and the boxed hashCode: IEEE == would make 0.0 equal -0.0 under different hashes.
internal fun sameFloat(a: Float, b: Float): Boolean = a.toBits() == b.toBits()

internal fun sameFloat(a: Float?, b: Float?): Boolean = if (a == null || b == null) a == b else sameFloat(a, b)

private fun requireSeconds(name: String, value: Duration) {
    require(value.isFinite() && !value.isNegative()) { "$name must be finite and not negative, was $value" }
}
