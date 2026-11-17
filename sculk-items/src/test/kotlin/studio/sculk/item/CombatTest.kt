package studio.sculk.item

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.failureOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The weapon, blocking, reach, swing and use components are data: compared and rejected without a server. */
@OptIn(SculkExperimental::class)
class CombatTest {
    private val generic = Key.key("minecraft:generic")
    private val ding = Key.key("minecraft:block.note_block.bell")

    private val shield = BlocksAttacks(
        blockDelay = 250.milliseconds,
        disableCooldownScale = 0.5f,
        damageReductions = listOf(BlocksAttacks.DamageReduction(types = setOf(generic), horizontalBlockingAngle = 60f, factor = 0.5f)),
        itemDamage = BlocksAttacks.ItemDamage(threshold = 3f, base = 1f, factor = 0.5f),
        bypassedBy = Key.key("minecraft:bypasses_shield"),
        blockSound = ding,
        disableSound = ding,
    )

    private val lance = KineticWeapon(
        contactCooldownTicks = 20,
        delayTicks = 5,
        dismountConditions = KineticWeapon.Condition(maxDurationTicks = 40, minSpeed = 0.5f),
        knockbackConditions = KineticWeapon.Condition(maxDurationTicks = 60),
        damageConditions = KineticWeapon.Condition(maxDurationTicks = 80, minRelativeSpeed = 0.2f),
        forwardMovement = 0.4f,
        damageMultiplier = 1.5f,
        sound = ding,
        hitSound = ding,
    )

    private val everything = item(Material.TRIDENT) {
        weapon = Weapon(damagePerAttack = 2, disablesBlockingFor = 5.seconds)
        blocksAttacks = shield
        attackRange = AttackRange(minReach = 1f, maxReach = 6f, maxCreativeReach = 8f)
        swingAnimation = SwingAnimation(SwingAnimation.Style.STAB, durationTicks = 10)
        useEffects = UseEffects(canSprint = true, speedMultiplier = 1f)
        piercingWeapon = PiercingWeapon(dismounts = true)
        kineticWeapon = lance
        minimumAttackCharge = 0.9f
    }

    /** Asserts [base] equals a fresh copy and differs from each variant, which changes exactly one field. */
    private fun <T : Any> assertEveryFieldCounts(base: T, again: T, variants: List<T>) {
        assertEquals(base, again)
        assertEquals(base.hashCode(), again.hashCode())
        variants.forEach { assertNotEquals(base, it, "$it") }
    }

    @Test
    fun `every field of a weapon takes part in its equality`() {
        assertEveryFieldCounts(Weapon(), Weapon(1, 0.seconds), listOf(Weapon(damagePerAttack = 2), Weapon(disablesBlockingFor = 1.seconds)))
    }

    @Test
    fun `every field of a blocking component and its parts takes part in its equality`() {
        val reduction = BlocksAttacks.DamageReduction()
        assertEveryFieldCounts(
            reduction,
            BlocksAttacks.DamageReduction(emptySet(), 90f, 0f, 1f),
            listOf(
                BlocksAttacks.DamageReduction(types = setOf(generic)),
                BlocksAttacks.DamageReduction(horizontalBlockingAngle = 45f),
                BlocksAttacks.DamageReduction(base = 1f),
                BlocksAttacks.DamageReduction(factor = 0.5f),
            ),
        )
        assertEveryFieldCounts(
            BlocksAttacks.ItemDamage(),
            BlocksAttacks.ItemDamage(1f, 0f, 1f),
            listOf(BlocksAttacks.ItemDamage(threshold = 2f), BlocksAttacks.ItemDamage(base = 1f), BlocksAttacks.ItemDamage(factor = 2f)),
        )
        assertEveryFieldCounts(
            BlocksAttacks(),
            BlocksAttacks(0.seconds, 1f, listOf(BlocksAttacks.DamageReduction()), BlocksAttacks.ItemDamage(), null, null, null),
            listOf(
                BlocksAttacks(blockDelay = 1.seconds),
                BlocksAttacks(disableCooldownScale = 0f),
                BlocksAttacks(damageReductions = emptyList()),
                BlocksAttacks(itemDamage = BlocksAttacks.ItemDamage(threshold = 0f)),
                BlocksAttacks(bypassedBy = Key.key("minecraft:bypasses_shield")),
                BlocksAttacks(blockSound = ding),
                BlocksAttacks(disableSound = ding),
            ),
        )
    }

    @Test
    fun `every field of an attack range takes part in its equality`() {
        assertEveryFieldCounts(
            AttackRange(),
            AttackRange(0f, 3f, 0f, 5f, 0.3f, 1f),
            listOf(
                AttackRange(minReach = 1f),
                AttackRange(maxReach = 4f),
                AttackRange(minCreativeReach = 1f),
                AttackRange(maxCreativeReach = 6f),
                AttackRange(hitboxMargin = 0f),
                AttackRange(mobFactor = 2f),
            ),
        )
    }

    @Test
    fun `every field of a swing animation and of use effects takes part in their equality`() {
        assertEveryFieldCounts(
            SwingAnimation(),
            SwingAnimation(SwingAnimation.Style.WHACK, 6),
            listOf(SwingAnimation(style = SwingAnimation.Style.STAB), SwingAnimation(durationTicks = 7)),
        )
        assertEveryFieldCounts(
            UseEffects(),
            UseEffects(canSprint = false, interactVibrations = true, speedMultiplier = 0.2f),
            listOf(UseEffects(canSprint = true), UseEffects(interactVibrations = false), UseEffects(speedMultiplier = 0.5f)),
        )
    }

    @Test
    fun `every field of a piercing and a kinetic weapon takes part in their equality`() {
        assertEveryFieldCounts(
            PiercingWeapon(),
            PiercingWeapon(dealsKnockback = true, dismounts = false, sound = null, hitSound = null),
            listOf(
                PiercingWeapon(dealsKnockback = false),
                PiercingWeapon(dismounts = true),
                PiercingWeapon(sound = ding),
                PiercingWeapon(hitSound = ding),
            ),
        )
        val condition = KineticWeapon.Condition(maxDurationTicks = 10)
        assertEveryFieldCounts(
            condition,
            KineticWeapon.Condition(10, 0f, 0f),
            listOf(
                KineticWeapon.Condition(maxDurationTicks = 11),
                KineticWeapon.Condition(maxDurationTicks = 10, minSpeed = 1f),
                KineticWeapon.Condition(maxDurationTicks = 10, minRelativeSpeed = 1f),
            ),
        )
        assertEveryFieldCounts(
            KineticWeapon(),
            KineticWeapon(10, 0, null, null, null, 0f, 1f, null, null),
            listOf(
                KineticWeapon(contactCooldownTicks = 0),
                KineticWeapon(delayTicks = 1),
                KineticWeapon(dismountConditions = condition),
                KineticWeapon(knockbackConditions = condition),
                KineticWeapon(damageConditions = condition),
                KineticWeapon(forwardMovement = 1f),
                KineticWeapon(damageMultiplier = 2f),
                KineticWeapon(sound = ding),
                KineticWeapon(hitSound = ding),
            ),
        )
    }

    @Test
    fun `negative zero is a different value from zero, so equal values always share a hash`() {
        assertNotEquals(AttackRange(minReach = 0f), AttackRange(minReach = -0f))
        assertNotEquals(item(Material.STICK) { minimumAttackCharge = 0f }, item(Material.STICK) { minimumAttackCharge = -0f })
    }

    @Test
    fun `values Minecraft would reject fail where they are declared`() {
        assertThrows<IllegalArgumentException> { Weapon(damagePerAttack = -1) }
        assertThrows<IllegalArgumentException> { Weapon(disablesBlockingFor = (-1).seconds) }
        assertThrows<IllegalArgumentException> { BlocksAttacks(blockDelay = (-1).seconds) }
        assertThrows<IllegalArgumentException> { BlocksAttacks(disableCooldownScale = -0.1f) }
        assertThrows<IllegalArgumentException> { BlocksAttacks.DamageReduction(horizontalBlockingAngle = 0f) }
        assertThrows<IllegalArgumentException> { BlocksAttacks.DamageReduction(base = Float.NaN) }
        assertThrows<IllegalArgumentException> { BlocksAttacks.ItemDamage(threshold = -1f) }
        assertThrows<IllegalArgumentException> { AttackRange(maxReach = 65f) }
        assertThrows<IllegalArgumentException> { AttackRange(minReach = 4f, maxReach = 3f) }
        assertThrows<IllegalArgumentException> { AttackRange(minCreativeReach = 6f) }
        assertThrows<IllegalArgumentException> { AttackRange(hitboxMargin = 1.5f) }
        assertThrows<IllegalArgumentException> { AttackRange(mobFactor = Float.NaN) }
        assertThrows<IllegalArgumentException> { SwingAnimation(durationTicks = 0) }
        assertThrows<IllegalArgumentException> { UseEffects(speedMultiplier = 1.5f) }
        assertThrows<IllegalArgumentException> { KineticWeapon(contactCooldownTicks = -1) }
        assertThrows<IllegalArgumentException> { KineticWeapon(delayTicks = -1) }
        assertThrows<IllegalArgumentException> { KineticWeapon(damageMultiplier = Float.POSITIVE_INFINITY) }
        assertThrows<IllegalArgumentException> { KineticWeapon.Condition(maxDurationTicks = -1) }
        assertThrows<IllegalArgumentException> { item(Material.STICK) { minimumAttackCharge = 1.5f } }
    }

    @Test
    fun `a blocking component keeps its own copy of the reductions and types it was given`() {
        val types = mutableSetOf(generic)
        val reductions = mutableListOf(BlocksAttacks.DamageReduction(types = types))
        val blocking = BlocksAttacks(damageReductions = reductions)
        types += Key.key("minecraft:arrow")
        reductions.clear()
        assertEquals(listOf(BlocksAttacks.DamageReduction(types = setOf(generic))), blocking.damageReductions)
    }

    @Test
    fun `a copy of a spec keeps every combat component`() {
        val copy = everything.with { name = "<primary>Lance" }.with { name = null }
        assertEquals(everything, copy)
        assertEquals(shield, copy.blocksAttacks)
        assertEquals(lance, copy.kineticWeapon)
    }

    @Test
    fun `the newer components a spec sets are listed in a fixed order`() {
        assertEquals(NewerComponent.entries, ItemCompiler.newerComponents(everything))
        assertEquals(emptyList<NewerComponent>(), ItemCompiler.newerComponents(item(Material.DIAMOND_SWORD) { unbreakable = true }))
    }

    // The test classpath carries the 1.21.4 floor API (sculk-items' testImplementation), which has none of these components.
    @Test
    fun `on the floor every newer component is missing, check names the properties to remove and create refuses`() {
        assertTrue(NewerComponent.entries.none { it.available })
        val failure = assertInstanceOf(SculkFailure.UnsupportedCapability::class.java, everything.check().failureOrNull())
        assertTrue("minecraft:weapon" in failure.capability && "minecraft:minimum_attack_charge" in failure.capability, failure.describe())
        assertTrue("weapon, blocksAttacks, attackRange" in failure.describe(), failure.describe())
        val refused = assertThrows<IllegalArgumentException> { everything.create() }
        assertTrue("minecraft:weapon" in refused.message.orEmpty(), refused.message)
    }

    @Test
    fun `a spec that sets no newer component is never refused for one`() {
        assertNull(ItemCompiler.unsupported(item(Material.DIAMOND_SWORD) { name = "<primary>Plain" }))
        assertEquals(listOf(NewerComponent.WEAPON), ItemCompiler.newerComponents(item(Material.STICK) { weapon = Weapon() }))
    }
}
