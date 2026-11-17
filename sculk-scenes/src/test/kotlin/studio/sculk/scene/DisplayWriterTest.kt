package studio.sculk.scene

import net.kyori.adventure.text.Component
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/** What reaches the entity: every property on spawn, and on an update only the parts that changed. */
class DisplayWriterTest {
    private val calls = mutableListOf<Pair<String, Any?>>()
    private val recorder = InvocationHandler { _, method, args ->
        require(method.name.startsWith("set") || method.name == "text") { "a writer only writes, but called ${method.name}" }
        calls += method.name to args?.singleOrNull()
        null
    }

    private val entity = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TextDisplay::class.java), recorder) as TextDisplay

    private val node = TextNode(
        "title",
        "Coins",
        TextStyle(shadow = true, lineWidth = 120),
        DisplayOptions(
            transform = Transform(translation = Vec3(0f, 1f, 0f)),
            billboard = Display.Billboard.CENTER,
            interpolationTicks = 10,
            interpolationDelayTicks = 2,
        ),
    )

    private fun names(): List<String> = calls.map { it.first }

    @Test
    fun `a new entity is never saved and gets every property before it joins the world`() {
        DisplayWriter.configure(entity, node, Content.Text(Component.text("Coins 10")), teleportTicks = 3, visibleByDefault = false)
        val written = calls.toMap()
        assertEquals(false, written["setPersistent"])
        assertEquals(false, written["setVisibleByDefault"])
        assertEquals(3, written["setTeleportDuration"])
        assertEquals(Display.Billboard.CENTER, written["setBillboard"])
        assertEquals(Component.text("Coins 10"), written["text"])
        assertEquals(true, written["setShadowed"])
        assertEquals(120, written["setLineWidth"])
        assertEquals(1f, (written["setTransformation"] as Transformation).translation.y)
    }

    @Test
    fun `a transform change carries its timing and writes nothing else`() {
        DisplayWriter.write(entity, node, setOf(Part.TRANSFORM), null)
        assertEquals(listOf("setInterpolationDuration", "setInterpolationDelay", "setTransformation"), names())
        assertEquals(10, calls[0].second)
        assertEquals(2, calls[1].second)
    }

    @Test
    fun `text is written only when there is new text to write`() {
        DisplayWriter.write(entity, node, setOf(Part.TEXT), null)
        assertEquals(emptyList<String>(), names(), "a render equal to what was sent writes nothing")
        DisplayWriter.write(entity, node, setOf(Part.TEXT), Content.Text(Component.text("Coins 11")))
        assertEquals(listOf("text"), names())
    }

    @Test
    fun `a style change rewrites the whole style and leaves text and pose alone`() {
        DisplayWriter.write(entity, node, setOf(Part.STYLE), null)
        val written = names()
        assertTrue("setShadowed" in written && "setLineWidth" in written && "setBackgroundColor" in written, "$written")
        assertTrue("text" !in written && "setTransformation" !in written, "$written")
    }
}
