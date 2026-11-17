package studio.sculk.item

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.failureOrNull
import studio.sculk.getOrNull
import studio.sculk.pdc.PdcCodec
import studio.sculk.pdc.PdcKey
import studio.sculk.pdc.read
import studio.sculk.pdc.write
import studio.sculk.testkit.paper.FakePersistentDataContainer
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The examples in docs/src/content/docs/guides/items.mdx that need no server, compiled and run;
 * `integration-tests/items` runs the rest on every anchor.
 */
class ItemsGuideTest {
    private val owner = PdcKey(NamespacedKey("shop", "owner"), PdcCodec.UUID)
    private val tags = PdcKey(NamespacedKey("shop", "tags"), PdcCodec.STRING.list())

    @Test
    fun `the guide's blade is plain data and a changed copy keeps the rest`() {
        val blade = item(Material.DIAMOND_SWORD) {
            name = "<gradient:#7f5af0:#2cb67d>Sculk Blade"
            lore("<muted>Hums when the Warden is near.")
            enchant(Key.key("minecraft:sharpness"), 5)
            hide(TooltipPart.ENCHANTMENTS, TooltipPart.ATTRIBUTES)
            unbreakable = true
        }
        val marked = blade.with { id = Key.key("shop:sculk_blade") }
        assertEquals(Key.key("shop:sculk_blade"), marked.id)
        assertEquals(blade.lore, marked.lore)
        assertEquals(setOf(TooltipPart.ENCHANTMENTS, TooltipPart.ATTRIBUTES), marked.hidden)
    }

    @Test
    @OptIn(SculkExperimental::class)
    fun `the guide's halberd and buckler are plain data, refused by a server without the combat components`() {
        val halberd = item(Material.IRON_AXE) {
            name = "<primary>Warden's Halberd"
            weapon = Weapon(damagePerAttack = 2, disablesBlockingFor = 5.seconds)
            attackRange = AttackRange(maxReach = 4.5f, maxCreativeReach = 6.5f)
            swingAnimation = SwingAnimation(SwingAnimation.Style.STAB, durationTicks = 10)
            minimumAttackCharge = 0.8f
        }
        val buckler = item(Material.SHIELD) {
            name = "<primary>Buckler"
            blocksAttacks = BlocksAttacks(
                blockDelay = 100.milliseconds,
                damageReductions = listOf(BlocksAttacks.DamageReduction(horizontalBlockingAngle = 60f, factor = 0.5f)),
                bypassedBy = Key.key("minecraft:bypasses_shield"),
                blockSound = Key.key("minecraft:item.shield.block"),
            )
            useEffects = UseEffects(canSprint = true, speedMultiplier = 0.6f)
        }
        assertEquals(halberd, halberd.with { name = "<primary>Warden's Halberd" })
        assertEquals(AttackRange(maxReach = 4.5f, maxCreativeReach = 6.5f), halberd.attackRange)
        // The test classpath carries the 1.21.4 floor API, where the guide says check() refuses these.
        assertInstanceOf(SculkFailure.UnsupportedCapability::class.java, buckler.check().failureOrNull())
    }

    @Test
    fun `the guide's persistent data entries read back what was written`() {
        val container = FakePersistentDataContainer()
        val id = UUID.randomUUID()
        container.write(owner, id)
        container.write(tags, listOf("rare", "shop"))
        assertEquals(id, container.read(owner).getOrNull())
        assertEquals(listOf("rare", "shop"), container.read(tags).getOrNull())
    }
}
