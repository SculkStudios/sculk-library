package studio.sculk.scene.testing

import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.execution.SculkScope
import studio.sculk.getOrThrow
import studio.sculk.placeholder.PlaceholderRegistry
import studio.sculk.placeholder.Refresh
import studio.sculk.scene.Anchor
import studio.sculk.scene.Fixtures
import studio.sculk.scene.Visibility
import studio.sculk.scene.scene
import studio.sculk.testkit.FakeExecutor

/** The fake behaves as the real scenes do wherever it can, and refuses where it cannot. */
class FakeScenesTest {
    private val scope = SculkScope.root("plugin", FakeExecutor()) {}
    private val placeholders = PlaceholderRegistry(scope)
    private val scenes = FakeScenes(placeholders)
    private val plain = PlainTextComponentSerializer.plainText()
    private val alex = Fixtures.player("Alex")
    private val sam = Fixtures.player("Sam")

    // Built past Anchor.at's world check, as there is no server to give a world; the fake never reads it.
    private val anchor = Anchor.At(Location(null, 0.0, 64.0, 0.0))

    init {
        placeholders.global("shop:online", Refresh.Live) { 3 }
        placeholders.audience("shop:name", Refresh.Live) { audience.getOrDefault(Identity.NAME, null) }
    }

    private val greeting = scene {
        text("title", "Online: <shop_online>")
        text("hello", "Hello <shop_name>", perViewer = true)
    }

    @Test
    fun `it refuses per-viewer text for everyone exactly as a server would`() {
        val refused = scenes.spawn(greeting, anchor, Visibility.Everyone)
        assertTrue(refused is SculkFailure.UnsupportedCapability, "$refused")
        assertEquals(emptyList<FakeScene>(), scenes.spawned)
    }

    @Test
    fun `it renders shared and per-viewer text with the placeholders a server render uses`() {
        val scene = scenes.spawn(greeting, anchor, Visibility.only(alex, sam)).getOrThrow() as FakeScene
        assertEquals("Online: 3", plain.serialize(scene.textOf("title")))
        assertEquals("Hello Alex", plain.serialize(scene.textOf("hello", Fixtures.audience("Alex"))))
        assertEquals("Hello Sam", plain.serialize(scene.textOf("hello", Fixtures.audience("Sam"))))
        assertThrows<IllegalArgumentException> { scene.textOf("hello") }
    }

    @Test
    fun `it keeps every spec, anchor, show and hide, and stops taking updates once closed`() {
        val scene = scenes.spawn(greeting, anchor, Visibility.only(alex)).getOrThrow() as FakeScene
        assertTrue(scene.isShownTo(alex))
        assertFalse(scene.isShownTo(sam))
        scene.show(sam)
        scene.hide(alex)
        assertEquals(Visibility.only(listOf(sam.uniqueId)), scene.visibility)

        val second = greeting.withText("title", "Players: <shop_online>")
        scene.update(second).getOrThrow()
        scene.refresh()
        scene.close()
        scene.update(greeting.withText("title", "ignored")).getOrThrow()
        assertEquals(listOf(greeting, second), scene.history)
        assertEquals(1, scene.refreshes)
        assertFalse(scene.isOpen)
        assertEquals(emptyList<FakeScene>(), scenes.open)
    }

    @Test
    fun `it cannot say whether anything spawned, because it has no world`() {
        val shown = scenes.spawn(scene { text("title", "x") }, anchor).getOrThrow()
        assertThrows<UnsupportedOperationException> { shown.isSpawned }
    }
}
