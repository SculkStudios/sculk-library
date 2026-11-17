package studio.sculk.example.shop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShopSettingsTest {
    private val bread = Offer("bread", 16, 2)

    @Test
    fun `a purchase costs the price times the quantity, within the allowed quantity`() {
        assertEquals(2, cost(bread, 1, 16))
        assertEquals(32, cost(bread, 16, 16))
        assertNull(cost(bread, 0, 16))
        assertNull(cost(bread, 17, 16))
    }
}
