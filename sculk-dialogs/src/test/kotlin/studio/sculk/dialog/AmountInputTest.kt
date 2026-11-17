package studio.sculk.dialog

import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.annotation.SculkExperimental
import java.lang.reflect.Proxy
import java.math.BigDecimal

@OptIn(SculkExperimental::class)
class AmountInputTest {
    // Only carried by the response, never called.
    private val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ ->
        error("the response must not call the player")
    } as Player

    private val form = dialog("Sell") {
        amount("price", "Price", min = BigDecimal.ONE, max = BigDecimal("1000000"), initial = BigDecimal("10000"))
        button("Sell")
    }

    private fun answer(typed: Any?) = DialogResponse(player, form, mapOf("price" to typed))

    @Test
    fun `an amount typed compactly reads back exactly`() {
        assertEquals(BigDecimal("1500"), answer("1.5k").amount("price"))
        assertEquals(BigDecimal("250"), answer("250").amount("price"))
        assertEquals(BigDecimal("1000000"), answer("1M").amount("price"))
    }

    @Test
    fun `an amount that is missing, not an amount or out of range reads as null`() {
        listOf(null, 5, "lots", "0", "2m", "-5k").forEach { assertNull(answer(it).amount("price"), "$it") }
    }

    @Test
    fun `an amount input fails where it is declared when its bounds cannot hold`() {
        assertThrows<IllegalArgumentException> { DialogInput.Amount("price", "Price", min = BigDecimal.TEN, max = BigDecimal.ONE) }
        assertThrows<IllegalArgumentException> { DialogInput.Amount("price", "Price", max = BigDecimal.TEN, initial = BigDecimal("11")) }
    }

    @Test
    fun `asking for an amount from another kind of input is a programming error`() {
        val other = dialog("Name") {
            text("price", "Price")
            button("Ok")
        }
        assertThrows<IllegalArgumentException> { DialogResponse(player, other, emptyMap()).amount("price") }
    }
}
