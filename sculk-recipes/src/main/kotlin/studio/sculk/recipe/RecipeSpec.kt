package studio.sculk.recipe

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.recipe.CookingBookCategory
import org.bukkit.inventory.recipe.CraftingBookCategory
import studio.sculk.annotation.SculkStable
import studio.sculk.item.ItemSpec

/**
 * A recipe as data, of any vanilla type, checked where it is built and compiled to Paper's recipe
 * by [Recipes.register]. Results are [ItemSpec]s, so a recipe can make a custom item.
 */
@SculkStable
public sealed class RecipeSpec(public val key: NamespacedKey) {
    /** A crafting grid pattern of up to three rows of up to three characters; a space is an empty slot. */
    @SculkStable
    public class Shaped(
        key: NamespacedKey,
        public val result: ItemSpec,
        rows: List<String>,
        keys: Map<Char, Ingredient>,
        public val group: String = "",
        public val category: CraftingBookCategory = CraftingBookCategory.MISC,
    ) : RecipeSpec(key) {
        public val rows: List<String> = rows.toList()
        public val keys: Map<Char, Ingredient> = keys.toMap()

        init {
            require(rows.size in 1..3) { "a shaped recipe has 1 to 3 rows, $key has ${rows.size}" }
            require(rows.all { it.length in 1..3 } && rows.map { it.length }.distinct().size == 1) {
                "every row of $key has the same length, 1 to 3: $rows"
            }
            val used = rows.joinToString("").toSet() - ' '
            require(used.isNotEmpty()) { "$key's pattern is empty" }
            require(' ' !in keys) { "a space is an empty slot in $key and cannot be a key" }
            require(used == keys.keys) { "$key's pattern uses ${used.sorted()} but keys ${keys.keys.sorted()}" }
        }
    }

    /** Crafting in any arrangement, from one to nine ingredients. */
    @SculkStable
    public class Shapeless(
        key: NamespacedKey,
        public val result: ItemSpec,
        ingredients: List<Ingredient>,
        public val group: String = "",
        public val category: CraftingBookCategory = CraftingBookCategory.MISC,
    ) : RecipeSpec(key) {
        public val ingredients: List<Ingredient> = ingredients.toList()

        init {
            require(ingredients.size in 1..9) { "a shapeless recipe takes 1 to 9 ingredients, $key takes ${ingredients.size}" }
        }
    }

    /** The block a [Cooking] recipe runs in. */
    @SculkStable
    public enum class Cooker {
        FURNACE,
        BLASTING,
        SMOKING,
        CAMPFIRE,
    }

    /** Smelting, blasting, smoking or campfire cooking. [ticks] defaults to vanilla's for the cooker. */
    @SculkStable
    public class Cooking(
        key: NamespacedKey,
        public val cooker: Cooker,
        public val input: Ingredient,
        public val result: ItemSpec,
        public val experience: Float = 0f,
        ticks: Int? = null,
        public val group: String = "",
        public val category: CookingBookCategory = CookingBookCategory.MISC,
    ) : RecipeSpec(key) {
        public val ticks: Int = ticks ?: when (cooker) {
            Cooker.FURNACE -> 200
            Cooker.BLASTING, Cooker.SMOKING -> 100
            Cooker.CAMPFIRE -> 600
        }

        init {
            require(experience >= 0f) { "$key's experience must not be negative" }
            require(this.ticks > 0) { "$key's cooking time must be positive" }
        }
    }

    @SculkStable
    public class Stonecutting(
        key: NamespacedKey,
        public val input: Ingredient,
        public val result: ItemSpec,
        public val group: String = "",
    ) : RecipeSpec(key)

    /** Upgrades [base] with [addition] into [result], keeping the base's components when [copyComponents] is set. */
    @SculkStable
    public class SmithingTransform(
        key: NamespacedKey,
        public val template: Ingredient,
        public val base: Ingredient,
        public val addition: Ingredient,
        public val result: ItemSpec,
        public val copyComponents: Boolean = true,
    ) : RecipeSpec(key)

    /**
     * Applies an armour trim. From 1.21.5 a trim recipe names its [pattern]; on 1.21.4 the template
     * item decides it and [pattern] is ignored.
     */
    @SculkStable
    public class SmithingTrim(
        key: NamespacedKey,
        public val template: Ingredient,
        public val base: Ingredient,
        public val addition: Ingredient,
        public val pattern: Key? = null,
        public val copyComponents: Boolean = true,
    ) : RecipeSpec(key)

    /** Crafts [input] with [material] into [result] keeping the input's components, as dyeing a shulker box does. */
    @SculkStable
    public class Transmute(
        key: NamespacedKey,
        public val input: Ingredient,
        public val material: Ingredient,
        public val result: Material,
        public val group: String = "",
        public val category: CraftingBookCategory = CraftingBookCategory.MISC,
    ) : RecipeSpec(key) {
        init {
            require(input !is Ingredient.Exact && material !is Ingredient.Exact) {
                "a transmute recipe matches by item type, not exact items"
            }
        }
    }

    /** Brews [ingredient] into [input] in a brewing stand, making [result]. */
    @SculkStable
    public class Brewing(
        key: NamespacedKey,
        public val input: Ingredient,
        public val ingredient: Ingredient,
        public val result: ItemSpec,
    ) : RecipeSpec(key)

    override fun toString(): String = "${this::class.simpleName}($key)"
}

/** Builds a [RecipeSpec.Shaped]; see [shaped]. */
@SculkStable
public class ShapedBuilder internal constructor() {
    internal val rows = mutableListOf<String>()
    internal val keys = linkedMapOf<Char, Ingredient>()
    public var group: String = ""
    public var category: CraftingBookCategory = CraftingBookCategory.MISC

    /** The pattern, top row first. */
    public fun rows(vararg rows: String) {
        this.rows.clear()
        this.rows += rows
    }

    /** What [symbol] in the pattern stands for. */
    public fun key(symbol: Char, ingredient: Ingredient) {
        keys[symbol] = ingredient
    }
}

/**
 * A shaped crafting recipe:
 *
 * ```kotlin
 * val bladeRecipe = shaped(NamespacedKey(plugin, "sculk_blade"), blade) {
 *     rows(" E ", " E ", " S ")
 *     key('E', Ingredient.of(Material.ECHO_SHARD))
 *     key('S', Ingredient.of(Material.STICK))
 * }
 * ```
 */
@SculkStable
public fun shaped(key: NamespacedKey, result: ItemSpec, build: ShapedBuilder.() -> Unit): RecipeSpec.Shaped {
    val builder = ShapedBuilder().apply(build)
    return RecipeSpec.Shaped(key, result, builder.rows, builder.keys, builder.group, builder.category)
}
