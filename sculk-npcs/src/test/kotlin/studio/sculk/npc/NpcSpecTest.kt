package studio.sculk.npc

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.MainHand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.item.item
import studio.sculk.text.Text
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Specs are checked without a server; `integration-tests/npcs` spawns them for a real client. */
class NpcSpecTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `an empty spec is a quiet, invulnerable, immovable NPC with Minecraft's own label`() {
        val spec = npc {}
        assertNull(spec.name)
        assertNull(spec.description)
        assertTrue(spec.showDescription)
        assertEquals(Skin.Default, spec.skin)
        assertEquals(NpcPose.STANDING, spec.pose)
        assertEquals(MainHand.RIGHT, spec.mainHand)
        assertNull(spec.lookAt)
        assertFalse(spec.collidable)
        assertTrue(spec.invulnerable)
        assertTrue(spec.silent)
        assertTrue(spec.immovable)
        assertEquals(200.milliseconds, spec.clickCooldown)
        assertNull(spec.onLeftClick)
        assertNull(spec.onRightClick)
    }

    @Test
    fun `values reach the name as plain text and never as markup`() {
        val spec = npc { name("<gold>Shopkeeper <who>", "who" to "<red>Mallory") }
        val name = requireNotNull(spec.name)
        assertEquals("Shopkeeper <red>Mallory", plain.serialize(name.render(Text())))
    }

    @Test
    fun `a description replaces the label and hiding it removes the line`() {
        val described = npc { description("<muted>Right-click to trade") }
        assertEquals("<muted>Right-click to trade", described.description?.template)
        assertTrue(described.showDescription)
        val hidden = described.with { hideDescription() }
        assertNull(hidden.description)
        assertFalse(hidden.showDescription)
    }

    @Test
    fun `a spec cannot be changed through what it was built from or what it hands out`() {
        lateinit var builder: NpcBuilder
        val spec = npc {
            builder = this
            equip(NpcSlot.HEAD, item(Material.DIAMOND_HELMET))
        }
        builder.equip(NpcSlot.FEET, item(Material.DIAMOND_BOOTS))
        assertEquals(setOf(NpcSlot.HEAD), spec.equipment.keys)
        val values = mutableMapOf("who" to "Ada")
        val text = NpcText("<who>", values)
        values["who"] = "Mallory"
        assertEquals(mapOf("who" to "Ada"), text.values)
    }

    @Test
    fun `with derives a variant and leaves the original as it was`() {
        val greet: (NpcClick) -> Unit = {}
        val base = npc {
            name("Guard")
            pose = NpcPose.SNEAKING
            onRightClick(greet)
        }
        val variant = base.with {
            name("Captain")
            lookAtPlayers(range = 12.0)
        }
        assertEquals("Guard", base.name?.template)
        assertNull(base.lookAt)
        assertEquals("Captain", variant.name?.template)
        assertEquals(LookAt(12.0, 2), variant.lookAt)
        assertEquals(NpcPose.SNEAKING, variant.pose)
        assertSame(greet, variant.onRightClick)
    }

    @Test
    fun `onClick answers both buttons with one handler`() {
        val handler: (NpcClick) -> Unit = {}
        val spec = npc { onClick(handler) }
        assertSame(handler, spec.onLeftClick)
        assertSame(handler, spec.onRightClick)
    }

    @Test
    fun `a spec that could not work fails where it is declared`() {
        assertThrows<IllegalArgumentException> { npc { name(" ") } }
        assertThrows<IllegalArgumentException> { npc { name("<bad key>", "bad key" to "x") } }
        assertThrows<IllegalArgumentException> { npc { lookAtPlayers(range = 0.5) } }
        assertThrows<IllegalArgumentException> { npc { lookAtPlayers(range = LookAt.MAX_RANGE + 1) } }
        assertThrows<IllegalArgumentException> { npc { lookAtPlayers(everyTicks = 0) } }
        assertThrows<IllegalArgumentException> { npc { clickCooldown = (-1).milliseconds } }
        assertThrows<IllegalArgumentException> { npc { clickCooldown = 2.minutes } }
        assertEquals(Duration.ZERO, npc { clickCooldown = Duration.ZERO }.clickCooldown)
    }

    @Test
    fun `every pose and slot maps to the Paper value a mannequin accepts`() {
        assertEquals(
            listOf("STANDING", "SNEAKING", "SWIMMING", "FALL_FLYING", "SLEEPING"),
            NpcPose.entries.map { it.bukkit().name },
        )
        assertEquals(
            listOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET"),
            NpcSlot.entries.map { it.bukkit().name },
        )
    }
}
