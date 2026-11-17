package studio.sculk.itest.recipes

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.SmithingTrimRecipe
import org.bukkit.inventory.StonecuttingRecipe
import org.bukkit.inventory.TransmuteRecipe
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.ResourceScope
import studio.sculk.item.item
import studio.sculk.recipe.Ingredient
import studio.sculk.recipe.RecipeSpec
import studio.sculk.recipe.Recipes
import studio.sculk.recipe.shaped
import java.io.File

/**
 * Registers one recipe of every type, crafts with the crafting ones through Paper's
 * `craftItemResult`, checks the rest by lookup, removes them all with their scope, writes
 * `results.properties` and stops the server.
 */
class RecipesCheckPlugin : JavaPlugin() {
    private val results = sortedMapOf<String, String>()
    private val plain = PlainTextComponentSerializer.plainText()
    private fun key(name: String) = NamespacedKey(this, name)

    private val blade = item(Material.DIAMOND_SWORD) { name = "Sculk Blade" }
    private val token = item(Material.PAPER) {
        name = "Shop Token"
        id = Key.key("sculk-itest:token")
    }

    override fun onEnable() {
        server.globalRegionScheduler.run(this) { check() }
    }

    private fun check() {
        val recipes = Recipes(server)
        val scope = ResourceScope.root("recipes-check")
        val specs = listOf(
            shaped(key("blade"), blade) {
                rows("E", "E", "S")
                key('E', Ingredient.of(Material.ECHO_SHARD))
                key('S', Ingredient.of(Material.STICK))
            },
            RecipeSpec.Shapeless(key("gravel"), item(Material.GRAVEL) { amount = 4 }, listOf(Ingredient.of(Material.FLINT), Ingredient.of(Material.ECHO_SHARD))),
            shaped(key("planked"), item(Material.BARREL)) {
                rows("PE")
                key('P', Ingredient.tag(Key.key("minecraft:planks")))
                key('E', Ingredient.of(Material.ECHO_SHARD))
            },
            RecipeSpec.Shapeless(key("redeem"), item(Material.EMERALD), listOf(Ingredient.exact(token), Ingredient.of(Material.ECHO_SHARD))),
            RecipeSpec.Cooking(key("blast"), RecipeSpec.Cooker.BLASTING, Ingredient.of(Material.ECHO_SHARD), item(Material.AMETHYST_SHARD)),
            RecipeSpec.Stonecutting(key("cut"), Ingredient.of(Material.ECHO_SHARD), item(Material.AMETHYST_SHARD) { amount = 2 }),
            RecipeSpec.SmithingTransform(
                key("upgrade"),
                Ingredient.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Material.IRON_SWORD),
                Ingredient.of(Material.ECHO_SHARD),
                blade,
            ),
            RecipeSpec.SmithingTrim(
                key("trim"),
                Ingredient.of(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                Ingredient.of(Material.IRON_CHESTPLATE),
                Ingredient.of(Material.ECHO_SHARD),
                pattern = Key.key("minecraft:sentry"),
            ),
            RecipeSpec.Transmute(key("dye"), Ingredient.of(Material.SHULKER_BOX), Ingredient.of(Material.ECHO_SHARD), Material.BLACK_SHULKER_BOX),
            RecipeSpec.Brewing(key("brew"), Ingredient.of(Material.POTION), Ingredient.of(Material.ECHO_SHARD), item(Material.EXPERIENCE_BOTTLE)),
        )
        verdict("every-type-registers") {
            recipes.register(scope, *specs.toTypedArray())
            val found = mapOf(
                "blast" to (server.getRecipe(key("blast")) as? BlastingRecipe)?.cookingTime,
                "cut" to (server.getRecipe(key("cut")) is StonecuttingRecipe),
                "upgrade" to (server.getRecipe(key("upgrade")) is SmithingTransformRecipe),
                "trim" to (server.getRecipe(key("trim")) is SmithingTrimRecipe),
                "dye" to (server.getRecipe(key("dye")) is TransmuteRecipe),
            )
            (found == mapOf("blast" to 100, "cut" to true, "upgrade" to true, "trim" to true, "dye" to true)) to "found $found"
        }
        verdict("shaped-crafts") {
            val made = craft(null, ItemStack.of(Material.ECHO_SHARD), null, null, ItemStack.of(Material.ECHO_SHARD), null, null, ItemStack.of(Material.STICK), null)
            val name = made.getData(DataComponentTypes.ITEM_NAME)?.let(plain::serialize)
            (made.type == Material.DIAMOND_SWORD && name == "Sculk Blade") to "made ${made.type} named $name"
        }
        verdict("shapeless-crafts") {
            val made = craft(ItemStack.of(Material.ECHO_SHARD), ItemStack.of(Material.FLINT))
            (made.type == Material.GRAVEL && made.amount == 4) to "made ${made.amount} ${made.type}"
        }
        verdict("tag-ingredient-takes-any-member") {
            val oak = craft(ItemStack.of(Material.OAK_PLANKS), ItemStack.of(Material.ECHO_SHARD)).type
            val birch = craft(ItemStack.of(Material.BIRCH_PLANKS), ItemStack.of(Material.ECHO_SHARD)).type
            (oak == Material.BARREL && birch == Material.BARREL) to "oak made $oak, birch made $birch"
        }
        verdict("exact-ingredient-takes-only-that-item") {
            val withToken = craft(token.create(), ItemStack.of(Material.ECHO_SHARD)).type
            val withPaper = craft(ItemStack.of(Material.PAPER), ItemStack.of(Material.ECHO_SHARD)).type
            (withToken == Material.EMERALD && withPaper != Material.EMERALD) to "token made $withToken, plain paper made $withPaper"
        }
        verdict("taken-keys-are-refused-and-nothing-stays") {
            val other = ResourceScope.root("duplicate")
            val refused = runCatching {
                recipes.register(other, RecipeSpec.Stonecutting(key("fresh"), Ingredient.of(Material.FLINT), item(Material.GRAVEL)), specs.first())
            }.exceptionOrNull()
            other.close()
            (refused is IllegalArgumentException && server.getRecipe(key("fresh")) == null) to "refused $refused, fresh ${server.getRecipe(key("fresh"))}"
        }
        verdict("removed-with-scope") {
            scope.close()
            val left = specs.map { it.key }.filter { server.getRecipe(it) != null }
            val crafted = craft(ItemStack.of(Material.ECHO_SHARD), ItemStack.of(Material.FLINT)).type
            (left.isEmpty() && crafted != Material.GRAVEL) to "still registered $left, crafting made $crafted"
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.shutdown()
    }

    /** The result of crafting [slots] in a 3x3 grid, filled row by row; empty when nothing matches. */
    private fun craft(vararg slots: ItemStack?): ItemStack {
        val grid = Array(9) { ItemStack.empty() }
        slots.forEachIndexed { index, stack -> if (stack != null) grid[index] = stack }
        return server.craftItemResult(grid, server.worlds.first()).result
    }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }
}
