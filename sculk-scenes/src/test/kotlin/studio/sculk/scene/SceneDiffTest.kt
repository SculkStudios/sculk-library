package studio.sculk.scene

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Display
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.item.item

/** What an update redraws: only the parts of the nodes that differ, and nothing for the rest. */
class SceneDiffTest {
    private val base = scene {
        text("title", "Coins <coins>")
        item("icon", item(Material.DIAMOND))
        block("base", Fixtures.block("minecraft:stone"))
    }

    @Test
    fun `an identical spec changes nothing`() {
        assertEquals(emptyList<NodeChange>(), SceneDiff.between(base, base.with {}))
    }

    @Test
    fun `new text redraws the text alone`() {
        val changes = SceneDiff.between(base, base.withText("title", "Gems <gems>"))
        assertEquals(listOf(NodeChange.Changed(base.withText("title", "Gems <gems>").requireNode("title"), setOf(Part.TEXT))), changes)
    }

    @Test
    fun `a moved node redraws its transform alone, and new timing travels with it`() {
        val moved = base.with {
            item("icon", item(Material.DIAMOND)) {
                transform = Transform(translation = Vec3(0f, 1f, 0f))
                interpolationTicks = 10
            }
        }
        val change = SceneDiff.between(base, moved).single() as NodeChange.Changed
        assertEquals("icon", change.id)
        assertEquals(setOf(Part.TRANSFORM, Part.TIMING), change.parts)
    }

    @Test
    fun `style, look, item and block changes are told apart`() {
        val changed = base.with {
            text("title", "Coins <coins>") { background = Color.fromARGB(0, 0, 0, 0) }
            item("icon", item(Material.EMERALD)) { billboard = Display.Billboard.CENTER }
            block("base", Fixtures.block("minecraft:dirt")) { viewRange = 2f }
        }
        val parts = SceneDiff.between(base, changed).associate { (it as NodeChange.Changed).id to it.parts }
        val expected = mapOf("title" to setOf(Part.STYLE), "icon" to setOf(Part.ITEM, Part.LOOK), "base" to setOf(Part.BLOCK, Part.LOOK))
        assertEquals(expected, parts)
    }

    @Test
    fun `a node of another kind under the same id is replaced`() {
        val changed = base.with { text("icon", "now text") }
        assertEquals(listOf(NodeChange.Replaced(changed.requireNode("icon"))), SceneDiff.between(base, changed))
    }

    @Test
    fun `text turning per-viewer or shared is replaced, as it needs other entities`() {
        val changed = base.with { text("title", "Coins <coins>", perViewer = true) }
        assertEquals(listOf(NodeChange.Replaced(changed.requireNode("title"))), SceneDiff.between(base, changed))
    }

    @Test
    fun `removals come first, then additions and changes in the new order`() {
        val changed = base.with {
            remove("base")
            text("footer", "bye")
            text("title", "Coins: <coins>")
        }
        assertEquals(
            listOf(
                NodeChange.Removed("base"),
                NodeChange.Changed(changed.requireNode("title"), setOf(Part.TEXT)),
                NodeChange.Added(changed.requireNode("footer")),
            ),
            SceneDiff.between(base, changed),
        )
    }
}
