package studio.sculk.dialog

import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Proxy

/** Specs and answers are checked without a server; `integration-tests/dialogs` shows them to a real client. */
class DialogSpecTest {
    // Only carried by the response, never called.
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ ->
        error("the response must not call the player")
    } as Player

    private val form = dialog("Set up your shop") {
        text("name", "Name", maxLength = 8)
        toggle("open", "Open now")
        number("price", "Price", 1f..100f, step = 1f, initial = 10f)
        choice("colour", "Colour", listOf("red" to "Red", "blue" to "Blue"), initial = "red")
        button("Save")
        button("Cancel")
    }

    @Test
    fun `answers of the declared kind and within bounds read back`() {
        val answer = DialogResponse(player, form, mapOf("name" to "Daisy", "open" to true, "price" to 42f, "colour" to "blue"))
        assertEquals("Daisy", answer.text("name"))
        assertEquals(true, answer.toggle("open"))
        assertEquals(42f, answer.number("price"))
        assertEquals("blue", answer.choice("colour"))
    }

    @Test
    fun `a client sending anything else gets null, not a crash`() {
        val answer = DialogResponse(player, form, mapOf("name" to "far too long", "open" to "yes", "price" to 1000f, "colour" to "green"))
        assertNull(answer.text("name"))
        assertNull(answer.toggle("open"))
        assertNull(answer.number("price"))
        assertNull(answer.choice("colour"))
    }

    @Test
    fun `asking for an input the dialog does not have is a programming error`() {
        val answer = DialogResponse(player, form, emptyMap())
        assertThrows<IllegalArgumentException> { answer.text("missing") }
        assertThrows<IllegalArgumentException> { answer.toggle("name") }
    }

    @Test
    fun `a dialog that cannot be shown fails where it is declared`() {
        assertThrows<IllegalArgumentException> { dialog("No buttons") {} }
        assertThrows<IllegalArgumentException> {
            dialog("Twice") {
                toggle("a", "A")
                toggle("a", "A again")
                button("OK")
            }
        }
        assertThrows<IllegalArgumentException> { dialog("Bad") { number("n", "N", 5f..5f) } }
        assertThrows<IllegalArgumentException> { dialog("Bad") { choice("c", "C", listOf("x" to "X"), initial = "y") } }
        assertThrows<IllegalArgumentException> { dialog("Bad") { text("bad key", "Label") } }
    }
}
