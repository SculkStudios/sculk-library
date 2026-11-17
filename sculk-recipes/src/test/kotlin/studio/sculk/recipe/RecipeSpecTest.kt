package studio.sculk.recipe

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.item.item

/** Recipes are checked where they are declared, without a server. */
class RecipeSpecTest {
    private val blade = item(Material.DIAMOND_SWORD)
    private val key = NamespacedKey("shop", "blade")
    private val shard = Ingredient.of(Material.ECHO_SHARD)
    private val stick = Ingredient.of(Material.STICK)

    @Test
    fun `a shaped recipe keeps its pattern and keys`() {
        val recipe = shaped(key, blade) {
            rows(" E ", " E ", " S ")
            key('E', shard)
            key('S', stick)
        }
        assertEquals(listOf(" E ", " E ", " S "), recipe.rows)
        assertEquals(mapOf('E' to shard, 'S' to stick), recipe.keys)
    }

    @Test
    fun `a pattern must be a grid whose symbols all have keys`() {
        assertThrows<IllegalArgumentException> { shaped(key, blade) { rows("EEEE") } }
        assertThrows<IllegalArgumentException> {
            shaped(key, blade) {
                rows("EE", "E")
                key('E', shard)
            }
        }
        assertThrows<IllegalArgumentException> {
            shaped(key, blade) {
                rows("ES")
                key('E', shard)
            }
        }
        assertThrows<IllegalArgumentException> {
            shaped(key, blade) {
                rows("E")
                key('E', shard)
                key('X', stick)
            }
        }
        assertThrows<IllegalArgumentException> { shaped(key, blade) { rows("   ") } }
    }

    @Test
    fun `shapeless recipes take one to nine ingredients`() {
        assertThrows<IllegalArgumentException> { RecipeSpec.Shapeless(key, blade, emptyList()) }
        assertThrows<IllegalArgumentException> { RecipeSpec.Shapeless(key, blade, List(10) { stick }) }
    }

    @Test
    fun `cooking defaults to vanilla's time for each cooker`() {
        val times = RecipeSpec.Cooker.entries.associateWith { RecipeSpec.Cooking(key, it, shard, blade).ticks }
        assertEquals(
            mapOf(
                RecipeSpec.Cooker.FURNACE to 200,
                RecipeSpec.Cooker.BLASTING to 100,
                RecipeSpec.Cooker.SMOKING to 100,
                RecipeSpec.Cooker.CAMPFIRE to 600,
            ),
            times,
        )
        assertThrows<IllegalArgumentException> { RecipeSpec.Cooking(key, RecipeSpec.Cooker.FURNACE, shard, blade, experience = -1f) }
    }

    @Test
    fun `transmuting matches by type, never by exact item`() {
        assertThrows<IllegalArgumentException> {
            RecipeSpec.Transmute(key, Ingredient.exact(blade), shard, Material.NETHERITE_SWORD)
        }
    }
}
