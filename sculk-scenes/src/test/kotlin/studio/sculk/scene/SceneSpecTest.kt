package studio.sculk.scene

import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.item.item

/** Scene specs: what they accept, what they refuse, and how copies change them. */
class SceneSpecTest {
    private val board = scene {
        text("title", "<primary>Rewards") { transform = Transform(translation = Vec3(0f, 0.6f, 0f)) }
        item("icon", item(Material.CHEST)) { itemTransform = ItemDisplay.ItemDisplayTransform.GUI }
        block("base", Fixtures.block("minecraft:stone"))
    }

    @Test
    fun `nodes keep their order and the defaults Minecraft uses`() {
        assertEquals(listOf("title", "icon", "base"), board.nodes.map { it.id })
        val title = board.requireNode("title") as TextNode
        assertEquals(Display.Billboard.CENTER, title.display.billboard, "text faces the viewer by default")
        assertEquals(TextStyle.DEFAULT_LINE_WIDTH, title.style.lineWidth)
        assertEquals(Display.Billboard.FIXED, board.requireNode("icon").display.billboard)
        assertEquals(0, board.teleportTicks)
    }

    @Test
    fun `node ids must be unique, short and plain`() {
        assertThrows<IllegalArgumentException> { TextNode("Title", "x") }
        assertThrows<IllegalArgumentException> { TextNode("", "x") }
        assertThrows<IllegalArgumentException> { TextNode("a".repeat(65), "x") }
        assertThrows<IllegalArgumentException> { SceneSpec(listOf(TextNode("a", "x"), TextNode("a", "y")), 0) }
        TextNode("line-1.part_2", "fine")
    }

    @Test
    fun `a spec refuses more nodes or longer gliding than Minecraft allows`() {
        assertThrows<IllegalArgumentException> { scene { teleportTicks = 60 } }
        assertThrows<IllegalArgumentException> { scene { teleportTicks = -1 } }
        assertThrows<IllegalArgumentException> {
            scene { repeat(SceneSpec.MAX_NODES + 1) { text("n$it", "x") } }
        }
        assertEquals(59, scene { teleportTicks = 59 }.teleportTicks)
    }

    @Test
    fun `display options refuse negative timing, negative range and a zero line width`() {
        assertThrows<IllegalArgumentException> { DisplayOptions(interpolationTicks = -1) }
        assertThrows<IllegalArgumentException> { DisplayOptions(interpolationDelayTicks = -1) }
        assertThrows<IllegalArgumentException> { DisplayOptions(viewRange = -0.5f) }
        assertThrows<IllegalArgumentException> { DisplayOptions(viewRange = Float.NaN) }
        assertThrows<IllegalArgumentException> { TextStyle(lineWidth = 0) }
    }

    @Test
    fun `a node added under an existing id replaces it where it stands`() {
        val changed = board.with { text("title", "<primary>New") }
        assertEquals(listOf("title", "icon", "base"), changed.nodes.map { it.id })
        assertEquals("<primary>New", (changed.requireNode("title") as TextNode).text)
        assertEquals("<primary>Rewards", (board.requireNode("title") as TextNode).text, "the original never changes")
    }

    @Test
    fun `removing and adding nodes in a copy leaves the original whole`() {
        val changed = board.with {
            remove("icon")
            text("footer", "<muted>Come back tomorrow")
        }
        assertEquals(listOf("title", "base", "footer"), changed.nodes.map { it.id })
        assertEquals(3, board.nodes.size)
        assertNull(changed.node("icon"))
    }

    @Test
    fun `withText and withTransform change one node and nothing else`() {
        val moved = Transform(translation = Vec3(0f, 2f, 0f))
        val changed = board.withText("title", "<primary>Today").withTransform("icon", moved)
        assertEquals("<primary>Today", (changed.requireNode("title") as TextNode).text)
        assertEquals(moved, changed.requireNode("icon").display.transform)
        assertEquals(board.requireNode("base"), changed.requireNode("base"))
        assertEquals(ItemDisplay.ItemDisplayTransform.GUI, (changed.requireNode("icon") as ItemNode).itemTransform)
    }

    @Test
    fun `changing a node that is missing or of another kind is a programming error`() {
        assertThrows<IllegalArgumentException> { board.withText("missing", "x") }
        assertThrows<IllegalArgumentException> { board.withText("icon", "x") }
        assertThrows<IllegalArgumentException> { board.change<TextNode>("title") { it.copy(id = "renamed") } }
    }

    @Test
    fun `a block node keeps the state it was made from`() {
        val node = board.requireNode("base") as BlockNode
        assertEquals("minecraft:stone", node.state)
        assertEquals(node, BlockNode("base", Fixtures.block("minecraft:stone")))
        assertEquals("minecraft:dirt", node.withBlock(Fixtures.block("minecraft:dirt")).state)
        assertEquals("other", node.copy(id = "other").id)
    }

    @Test
    fun `specs with the same nodes are equal`() {
        val again = scene {
            text("title", "<primary>Rewards") { transform = Transform(translation = Vec3(0f, 0.6f, 0f)) }
            item("icon", item(Material.CHEST)) { itemTransform = ItemDisplay.ItemDisplayTransform.GUI }
            block("base", Fixtures.block("minecraft:stone"))
        }
        assertEquals(board, again)
        assertEquals(board.hashCode(), again.hashCode())
    }
}
