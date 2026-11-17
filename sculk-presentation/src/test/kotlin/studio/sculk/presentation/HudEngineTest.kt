package studio.sculk.presentation

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Proxy

/** The render graph without a server: what is sent, when, and what is not. */
class HudEngineTest {
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ ->
        error("the engine must not call the player")
    } as Player

    private val values = mutableMapOf("coins" to "10", "online" to "3")

    // `<coins>` and `<online>` stand for placeholders: rendering records them as used.
    private fun render(template: String): Rendered {
        val used = values.keys.filter { "<$it>" in template }.toSet()
        val text = used.fold(template) { acc, key -> acc.replace("<$key>", values.getValue(key)) }
        return Rendered(Component.text(text), used)
    }

    private class Recorder : HudSink {
        val sent = mutableListOf<String>()
        var yieldSidebar = false
        private val plain = PlainTextComponentSerializer.plainText()

        override fun actionBar(text: Component) {
            sent += "bar:${plain.serialize(text)}"
        }

        override fun bar(id: String, shown: Shown.Bar?) {
            sent += "boss:$id:${shown?.let { plain.serialize(it.title) + "@" + it.progress } ?: "hidden"}"
        }

        override fun tabList(shown: Shown.TabList?) {
            sent += "tab:${shown?.let { plain.serialize(it.header) } ?: "hidden"}"
        }

        var yieldNames = false

        override fun nametag(shown: Shown.Nametag?): Boolean {
            if (yieldNames && shown != null) return false
            sent += "tag:${shown?.let { plain.serialize(it.prefix) + "|" + plain.serialize(it.suffix) } ?: "hidden"}"
            return true
        }

        override fun belowName(shown: Shown.BelowName?): Boolean {
            if (yieldNames && shown != null) return false
            sent += "below:${shown?.let { plain.serialize(it.text) } ?: "hidden"}"
            return true
        }

        override fun sidebar(shown: Shown.Sidebar?): Boolean {
            if (yieldSidebar && shown != null) return false
            sent += "side:${shown?.lines?.joinToString("|") { plain.serialize(it) } ?: "hidden"}"
            return true
        }
    }

    private val sink = Recorder()

    private fun engine(build: HudBuilder.() -> Unit) = HudEngine(hud(build), player, ::render, sink)

    @Test
    fun `the first wake draws everything and a quiet HUD sends nothing more`() {
        val hud = engine {
            tabList("Online <online>", "footer")
            sidebar("Title", "Coins <coins>", "Static")
        }
        assertNull(hud.wake(0))
        assertEquals(listOf("tab:Online 3", "side:Coins 10|Static"), sink.sent)
        sink.sent.clear()
        assertNull(hud.wake(500))
        assertEquals(emptyList<String>(), sink.sent)
    }

    @Test
    fun `a placeholder change redraws only the surfaces that used it, and only when the output changed`() {
        val hud = engine {
            tabList("Online <online>", "footer")
            sidebar("Title", "Coins <coins>")
        }
        hud.wake(0)
        sink.sent.clear()
        assertEquals(false, hud.mark("unused"))
        assertEquals(true, hud.mark("coins"))
        hud.wake(1)
        assertEquals(emptyList<String>(), sink.sent, "the value did not change, so nothing is sent")
        values["coins"] = "11"
        hud.mark("coins")
        hud.wake(2)
        assertEquals(listOf("side:Coins 11"), sink.sent)
    }

    @Test
    fun `an interval redraws on time and says when to wake next`() {
        val hud = engine { bar("event", "Coins <coins>", update = Update.Every(20)) }
        assertEquals(20L, hud.wake(0))
        values["coins"] = "12"
        assertEquals(20L, hud.wake(5))
        assertEquals(listOf("boss:event:Coins 10@1.0"), sink.sent)
        assertEquals(40L, hud.wake(20))
        assertEquals(listOf("boss:event:Coins 10@1.0", "boss:event:Coins 12@1.0"), sink.sent)
    }

    @Test
    fun `a condition hides and shows its surface and is checked every second`() {
        var shown = true
        val hud = engine { tabList("Online <online>", "footer", showWhen = { shown }) }
        assertEquals(20L, hud.wake(0))
        shown = false
        hud.wake(20)
        shown = true
        hud.wake(40)
        assertEquals(listOf("tab:Online 3", "tab:hidden", "tab:Online 3"), sink.sent)
    }

    @Test
    fun `an action bar is resent before the client fades it`() {
        val hud = engine { actionBar("Coins <coins>") }
        assertEquals(40L, hud.wake(0))
        hud.wake(10)
        assertEquals(80L, hud.wake(40))
        assertEquals(listOf("bar:Coins 10", "bar:Coins 10"), sink.sent)
    }

    @Test
    fun `a sidebar yields to another plugin's board and tries again`() {
        sink.yieldSidebar = true
        val hud = engine { sidebar("Title", "Coins <coins>") }
        assertEquals(20L, hud.wake(0))
        assertEquals(emptyList<String>(), sink.sent)
        sink.yieldSidebar = false
        assertNull(hud.wake(20))
        assertEquals(listOf("side:Coins 10"), sink.sent)
    }

    @Test
    fun `a nametag and below-name line redraw on change and yield to another plugin's team or slot`() {
        sink.yieldNames = true
        val hud = engine {
            nametag(prefix = "[<coins>] ")
            belowName("Coins <coins>")
        }
        assertEquals(20L, hud.wake(0))
        assertEquals(emptyList<String>(), sink.sent)
        sink.yieldNames = false
        assertNull(hud.wake(20))
        assertEquals(listOf("tag:[10] |", "below:Coins 10"), sink.sent)
        values["coins"] = "11"
        assertEquals(true, hud.mark("coins"))
        hud.wake(21)
        assertEquals(listOf("tag:[10] |", "below:Coins 10", "tag:[11] |", "below:Coins 11"), sink.sent)
        hud.close()
        assertEquals(listOf("tag:hidden", "below:hidden"), sink.sent.takeLast(2))
    }

    @Test
    fun `closing removes everything that is shown`() {
        val hud = engine {
            bar("a", "A")
            tabList("H", "F")
            sidebar("Title", "Line")
        }
        hud.wake(0)
        sink.sent.clear()
        hud.close()
        assertEquals(listOf("boss:a:hidden", "tab:hidden", "side:hidden"), sink.sent)
    }

    @Test
    fun `closing hides the nametag and below-name line before the sidebar gives its board back`() {
        val hud = engine {
            sidebar("Title", "Line")
            nametag("[x]")
            belowName("y")
        }
        hud.wake(0)
        sink.sent.clear()
        hud.close()
        assertEquals(listOf("tag:hidden", "below:hidden", "side:hidden"), sink.sent)
    }

    @Test
    fun `a layout shows one of each surface at most`() {
        assertThrows<IllegalArgumentException> {
            hud {
                tabList("a", "b")
                tabList("c", "d")
            }
        }
        assertThrows<IllegalArgumentException> {
            hud {
                bar("x", "one")
                bar("x", "two")
            }
        }
        assertThrows<IllegalArgumentException> { hud { sidebar("t", *Array(16) { "line" }) } }
        assertThrows<IllegalArgumentException> {
            hud {
                nametag("a")
                nametag("b")
            }
        }
    }
}
