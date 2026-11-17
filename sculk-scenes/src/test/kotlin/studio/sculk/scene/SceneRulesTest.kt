package studio.sculk.scene

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.placeholder.PlaceholderChange
import studio.sculk.placeholder.PlaceholderKey
import java.util.UUID

/** The decisions a scene takes without a server: what it may show, which entities it needs, what redraws, how it follows. */
class SceneRulesTest {
    private val alex = UUID.nameUUIDFromBytes("alex".toByteArray())
    private val sam = UUID.nameUUIDFromBytes("sam".toByteArray())
    private val target = UUID.nameUUIDFromBytes("target".toByteArray())
    private val greeting = scene {
        text("title", "Welcome")
        text("hello", "Hello <player_name>", perViewer = true)
    }

    @Test
    fun `per-viewer text for everyone in range is refused, naming the node and the way out`() {
        val refused = SceneRules.check(greeting, Viewers.of(Visibility.Everyone))
        assertTrue(refused is SculkFailure.UnsupportedCapability, "$refused")
        assertTrue("hello" in (refused as SculkFailure).describe())
        assertTrue("Visibility.only" in refused.describe())
        assertEquals(SculkResult.ok(), SceneRules.check(greeting, Viewers.of(Visibility.only(listOf(alex)))))
        assertEquals(SculkResult.ok(), SceneRules.check(scene { text("title", "Welcome") }, Viewers.of(Visibility.Everyone)))
    }

    @Test
    fun `shared nodes need one entity and per-viewer text one per online chosen viewer`() {
        val viewers = Viewers.of(Visibility.only(listOf(alex, sam)))
        val slots = SceneRules.slots(greeting, viewers) { it == alex }
        assertEquals(listOf(SlotKey("title", null), SlotKey("hello", alex)), slots)
        val both = SceneRules.slots(greeting, viewers) { true }
        assertEquals(setOf(SlotKey("title", null), SlotKey("hello", alex), SlotKey("hello", sam)), both.toSet())
    }

    @Test
    fun `showing and hiding changes the chosen set, or the hidden set when everyone sees it`() {
        val only = Viewers.of(Visibility.only(listOf(alex)))
        assertTrue(only.sees(alex))
        assertFalse(only.sees(sam))
        assertTrue(only.show(sam).sees(sam))
        assertFalse(only.hide(alex).sees(alex))
        assertEquals(setOf(sam, alex), only.changed(only.show(sam).hide(alex)))

        val everyone = Viewers.of(Visibility.Everyone)
        assertTrue(everyone.sees(sam))
        assertFalse(everyone.hide(sam).sees(sam))
        assertTrue(everyone.hide(sam).show(sam).sees(sam))
        assertEquals(Visibility.Everyone, everyone.hide(sam).asVisibility())
        assertEquals(Visibility.only(listOf(alex, sam)), only.show(sam).asVisibility())
    }

    @Test
    fun `a placeholder change reaches only the entities whose render it can alter`() {
        val key = PlaceholderKey.parse("shop:coins")
        val shared = SlotKey("title", null)
        val alexCopy = SlotKey("hello", alex)
        assertTrue(SceneRules.affects(PlaceholderChange.Everyone(key), shared, null))
        assertFalse(SceneRules.affects(PlaceholderChange.Audience(key, alex), shared, null), "shared text renders for no audience")
        assertTrue(SceneRules.affects(PlaceholderChange.Audience(key, target), shared, target), "shared text renders for the target")
        assertTrue(SceneRules.affects(PlaceholderChange.Audience(key, alex), alexCopy, null))
        assertFalse(SceneRules.affects(PlaceholderChange.Audience(key, sam), alexCopy, null))
        assertTrue(SceneRules.affects(PlaceholderChange.Relation(key, alex, target), alexCopy, target))
        assertFalse(SceneRules.affects(PlaceholderChange.Relation(key, alex, sam), alexCopy, target))
        assertFalse(SceneRules.affects(PlaceholderChange.Relation(key, alex, target), shared, target), "shared text has no viewer")
    }

    @Test
    fun `following glides over short moves, stays still for noise and respawns after a jump or a world change`() {
        assertEquals(SceneRules.Step.STAY, SceneRules.step(true, 0.0))
        assertEquals(SceneRules.Step.STAY, SceneRules.step(true, 1e-8))
        assertEquals(SceneRules.Step.GLIDE, SceneRules.step(true, 0.25))
        assertEquals(SceneRules.Step.GLIDE, SceneRules.step(true, Scene.JUMP_BLOCKS * Scene.JUMP_BLOCKS))
        assertEquals(SceneRules.Step.RESPAWN, SceneRules.step(true, Scene.JUMP_BLOCKS * Scene.JUMP_BLOCKS + 1))
        assertEquals(SceneRules.Step.RESPAWN, SceneRules.step(false, 0.0))
    }
}
