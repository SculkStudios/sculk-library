package studio.sculk.recipe

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.recipe.CookingBookCategory
import org.bukkit.inventory.recipe.CraftingBookCategory
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
    fun `the shapeless builder repeats an ingredient by its count and keeps the book settings`() {
        val recipe = shapeless(key, blade) {
            ingredient(shard, count = 2)
            ingredient(stick)
            group = "blades"
            category = CraftingBookCategory.EQUIPMENT
        }
        assertEquals(listOf(shard, shard, stick), recipe.ingredients)
        assertEquals("blades", recipe.group)
        assertEquals(CraftingBookCategory.EQUIPMENT, recipe.category)
        assertThrows<IllegalArgumentException> { shapeless(key, blade) {} }
        assertThrows<IllegalArgumentException> { shapeless(key, blade) { ingredient(shard, count = 0) } }
        assertThrows<IllegalArgumentException> { shapeless(key, blade) { ingredient(shard, count = 10) } }
    }

    @Test
    fun `the cooking builder takes vanilla's time unless told otherwise`() {
        val smelted = cooking(key, RecipeSpec.Cooker.FURNACE, shard, blade)
        assertEquals(200, smelted.ticks)
        assertEquals(0f, smelted.experience)
        val smoked = cooking(key, RecipeSpec.Cooker.SMOKING, shard, blade) {
            experience = 0.5f
            ticks = 40
            group = "food"
            category = CookingBookCategory.FOOD
        }
        assertEquals(RecipeSpec.Cooker.SMOKING, smoked.cooker)
        assertEquals(40, smoked.ticks)
        assertEquals(0.5f, smoked.experience)
        assertEquals("food", smoked.group)
        assertEquals(CookingBookCategory.FOOD, smoked.category)
        assertThrows<IllegalArgumentException> { cooking(key, RecipeSpec.Cooker.FURNACE, shard, blade) { ticks = 0 } }
    }

    @Test
    fun `the stonecutting builder keeps its input, result and group`() {
        val recipe = stonecutting(key, shard, blade) { group = "cut" }
        assertEquals(shard, recipe.input)
        assertEquals(blade, recipe.result)
        assertEquals("cut", recipe.group)
        assertEquals("", stonecutting(key, shard, blade).group)
    }

    @Test
    fun `the smithing builders copy the base's components unless told not to`() {
        val template = Ingredient.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
        val upgrade = smithingTransform(key, template, stick, shard, blade)
        assertEquals(true, upgrade.copyComponents)
        assertEquals(listOf(template, stick, shard), listOf(upgrade.template, upgrade.base, upgrade.addition))
        assertEquals(false, smithingTransform(key, template, stick, shard, blade) { copyComponents = false }.copyComponents)
        val pattern = Key.key("minecraft:silence")
        val trim = smithingTrim(key, template, stick, shard, pattern) { copyComponents = false }
        assertEquals(pattern, trim.pattern)
        assertEquals(false, trim.copyComponents)
    }

    @Test
    fun `the transmute builder keeps the book settings and still refuses exact items`() {
        val recipe = transmute(key, stick, shard, Material.NETHERITE_SWORD) { group = "upgrades" }
        assertEquals(Material.NETHERITE_SWORD, recipe.result)
        assertEquals("upgrades", recipe.group)
        assertEquals(CraftingBookCategory.MISC, recipe.category)
        assertThrows<IllegalArgumentException> { transmute(key, Ingredient.exact(blade), shard, Material.NETHERITE_SWORD) }
    }

    @Test
    fun `a brewing mix keeps its input, ingredient and result`() {
        val mix = brewing(key, stick, shard, blade)
        assertEquals(listOf(stick, shard), listOf(mix.input, mix.ingredient))
        assertEquals(blade, mix.result)
    }

    @Test
    fun `transmuting matches by type, never by exact item`() {
        assertThrows<IllegalArgumentException> {
            RecipeSpec.Transmute(key, Ingredient.exact(blade), shard, Material.NETHERITE_SWORD)
        }
    }
}
