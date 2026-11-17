package studio.sculk.dialog

import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.annotation.SculkExperimental
import java.lang.reflect.Proxy
import java.math.BigDecimal

@OptIn(SculkExperimental::class)
class TypedAnswersTest {
    // Only carried by the response, never called.
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ ->
        error("the response must not call the player")
    } as Player

    @Test
    fun `an input returned by the builder reads its own answer with its own type`() {
        lateinit var name: DialogInput.Text
        lateinit var open: DialogInput.Toggle
        lateinit var price: DialogInput.Amount
        val form = dialog("Shop") {
            name = text("name", "Name")
            open = toggle("open", "Open")
            price = amount("price", "Price")
            button("Save")
        }
        val answer = DialogResponse(player, form, mapOf("name" to "Daisy", "open" to true, "price" to "1.5k"))
        assertEquals("Daisy", answer[name])
        assertEquals(true, answer[open])
        assertEquals(BigDecimal("1500"), answer[price])
    }

    @Test
    fun `an input from another dialog is refused even when its key matches`() {
        val form = dialog("Shop") {
            text("name", "Name")
            button("Save")
        }
        lateinit var elsewhere: DialogInput.Text
        dialog("Other") {
            elsewhere = text("name", "Name")
            button("Ok")
        }
        assertThrows<IllegalArgumentException> { DialogResponse(player, form, mapOf("name" to "x"))[elsewhere] }
    }
}
