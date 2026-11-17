package studio.sculk.item

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import studio.sculk.ResourceScope
import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental
import studio.sculk.failureOrNull

/** What CustomItems refuses before it touches a server: amounts and ids that may come from configuration. */
class CustomItemsTest {
    private val scope = ResourceScope.root("test")
    private val items = CustomItems()
    private val gem = Key.key("shop:gem")

    private class Recording : CustomItemProvider {
        val asked = mutableListOf<Int>()
        override val namespace: String = "other"

        override fun create(id: Key, amount: Int): ItemStack? {
            asked += amount
            return null
        }

        override fun identify(stack: ItemStack): Key? = null
    }

    @Test
    fun `an amount outside one to a full stack is invalid input, not an exception`() {
        items.define(scope, item(Material.EMERALD) { id = gem })
        listOf(0, -1, ItemSpec.MAX_STACK + 1).forEach { amount ->
            val failure = assertInstanceOf(SculkFailure.InvalidInput::class.java, items.create(gem, amount).failureOrNull())
            assertEquals(amount.toString(), failure.received)
        }
    }

    @Test
    fun `an amount above the item's own stack size is invalid input`() {
        items.define(
            scope,
            item(Material.EMERALD) {
                id = gem
                maxStackSize = 16
            },
        )
        val failure = assertInstanceOf(SculkFailure.InvalidInput::class.java, items.create(gem, 17).failureOrNull())
        assertEquals("1 to 16", failure.expected)
    }

    @Test
    fun `an id nobody defines or provides is invalid input`() {
        assertInstanceOf(SculkFailure.InvalidInput::class.java, items.create(Key.key("shop:missing")).failureOrNull())
    }

    // The test classpath carries the 1.21.4 floor API, which has no weapon component.
    @Test
    @OptIn(SculkExperimental::class)
    fun `a defined item that sets a component the server lacks is an unsupported capability, not an exception`() {
        items.define(
            scope,
            item(Material.IRON_SWORD) {
                id = gem
                weapon = Weapon(damagePerAttack = 0)
            },
        )
        val failure = assertInstanceOf(SculkFailure.UnsupportedCapability::class.java, items.create(gem).failureOrNull())
        assertEquals("Item data minecraft:weapon", failure.capability)
    }

    @Test
    fun `a provider is asked only for amounts a stack can hold`() {
        val provider = Recording()
        items.register(scope, provider)
        items.create(Key.key("other:ruby"), 100)
        items.create(Key.key("other:ruby"), 0)
        items.create(Key.key("other:ruby"), 5)
        assertEquals(listOf(5), provider.asked)
    }
}
