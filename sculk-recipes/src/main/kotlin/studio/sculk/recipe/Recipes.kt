package studio.sculk.recipe

import io.papermc.paper.potion.PotionMix
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.Tag
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.CampfireRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.SmithingTrimRecipe
import org.bukkit.inventory.SmokingRecipe
import org.bukkit.inventory.StonecuttingRecipe
import org.bukkit.inventory.TransmuteRecipe
import org.bukkit.inventory.meta.trim.TrimPattern
import studio.sculk.ResourceScope
import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Registers [RecipeSpec]s with the server for as long as a scope lives.
 *
 * Recipes are server-wide state: call [register] in the global context (the main thread on Paper,
 * the global region on Folia and Canvas), as a plugin's start does. Players see new recipes as
 * soon as they are registered; closing the scope removes them.
 */
@SculkStable
public class Recipes(private val server: Server = Bukkit.getServer()) {
    /**
     * Adds every one of [specs] and returns a handle that removes them, also run when [scope]
     * closes. Throws [IllegalArgumentException] when a key is taken, a tag or trim pattern does not
     * exist, or the server refuses a recipe; nothing stays registered in that case.
     */
    public fun register(scope: ResourceScope, vararg specs: RecipeSpec): SculkHandle {
        require(specs.map { it.key }.distinct().size == specs.size) { "each recipe key may appear once" }
        val added = mutableListOf<RecipeSpec>()
        try {
            specs.forEach { spec ->
                add(spec)
                added += spec
            }
        } catch (failure: RuntimeException) {
            added.forEach(::remove)
            throw failure
        }
        val removal = SculkHandle.once { added.forEach(::remove) }
        val owned = scope.onClose(removal::close)
        return SculkHandle.once {
            owned.close()
            removal.close()
        }
    }

    private fun add(spec: RecipeSpec) {
        if (spec !is RecipeSpec.Brewing) require(server.getRecipe(spec.key) == null) { "the recipe key ${spec.key} is already registered" }
        val recipe: Recipe = when (spec) {
            is RecipeSpec.Brewing -> {
                server.potionBrewer.addPotionMix(PotionMix(spec.key, spec.result.create(), choice(spec.input), choice(spec.ingredient)))
                return
            }

            is RecipeSpec.Shaped -> ShapedRecipe(spec.key, spec.result.create()).apply {
                shape(*spec.rows.toTypedArray())
                spec.keys.forEach { (symbol, ingredient) -> setIngredient(symbol, choice(ingredient)) }
                group = spec.group
                category = spec.category
            }

            is RecipeSpec.Shapeless -> ShapelessRecipe(spec.key, spec.result.create()).apply {
                spec.ingredients.forEach { addIngredient(choice(it)) }
                group = spec.group
                category = spec.category
            }

            is RecipeSpec.Cooking -> {
                val result = spec.result.create()
                val input = choice(spec.input)
                when (spec.cooker) {
                    RecipeSpec.Cooker.FURNACE -> FurnaceRecipe(spec.key, result, input, spec.experience, spec.ticks)
                    RecipeSpec.Cooker.BLASTING -> BlastingRecipe(spec.key, result, input, spec.experience, spec.ticks)
                    RecipeSpec.Cooker.SMOKING -> SmokingRecipe(spec.key, result, input, spec.experience, spec.ticks)
                    RecipeSpec.Cooker.CAMPFIRE -> CampfireRecipe(spec.key, result, input, spec.experience, spec.ticks)
                }.apply {
                    group = spec.group
                    category = spec.category
                }
            }

            is RecipeSpec.Stonecutting -> StonecuttingRecipe(spec.key, spec.result.create(), choice(spec.input)).apply {
                group = spec.group
            }

            is RecipeSpec.SmithingTransform ->
                SmithingTransformRecipe(
                    spec.key,
                    spec.result.create(),
                    choice(spec.template),
                    choice(spec.base),
                    choice(spec.addition),
                    spec.copyComponents,
                )

            is RecipeSpec.SmithingTrim -> trim(spec)

            is RecipeSpec.Transmute -> TransmuteRecipe(spec.key, spec.result, choice(spec.input), choice(spec.material)).apply {
                group = spec.group
                category = spec.category
            }
        }
        require(server.addRecipe(recipe, true)) { "the server refused recipe ${spec.key}" }
    }

    private fun remove(spec: RecipeSpec) {
        if (spec is RecipeSpec.Brewing) server.potionBrewer.removePotionMix(spec.key) else server.removeRecipe(spec.key, true)
    }

    private fun trim(spec: RecipeSpec.SmithingTrim): Recipe {
        val withPattern = TRIM_WITH_PATTERN ?: return SmithingTrimRecipe(
            spec.key,
            choice(spec.template),
            choice(spec.base),
            choice(spec.addition),
            spec.copyComponents,
        )
        val patternKey = requireNotNull(spec.pattern) { "from Minecraft 1.21.5 a trim recipe names its pattern; ${spec.key} has none" }
        val pattern = requireNotNull(RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(patternKey)) {
            "no trim pattern $patternKey is registered"
        }
        return withPattern.invoke(
            spec.key,
            choice(spec.template),
            choice(spec.base),
            choice(spec.addition),
            pattern,
            spec.copyComponents,
        ) as Recipe
    }

    private fun choice(ingredient: Ingredient): RecipeChoice = when (ingredient) {
        is Ingredient.Items -> RecipeChoice.MaterialChoice(ingredient.materials.toList())

        is Ingredient.Tag -> RecipeChoice.MaterialChoice(
            requireNotNull(
                server.getTag(Tag.REGISTRY_ITEMS, NamespacedKey(ingredient.key.namespace(), ingredient.key.value()), Material::class.java),
            ) {
                "no item tag ${ingredient.key.asString()} exists"
            },
        )

        is Ingredient.Exact -> RecipeChoice.ExactChoice(ingredient.specs.map { it.create() })
    }

    private companion object {
        // From 1.21.5 a trim recipe takes its pattern; the constructor does not exist on 1.21.4.
        val TRIM_WITH_PATTERN: MethodHandle? = runCatching {
            MethodHandles.publicLookup().findConstructor(
                SmithingTrimRecipe::class.java,
                MethodType.methodType(
                    Void.TYPE,
                    NamespacedKey::class.java,
                    RecipeChoice::class.java,
                    RecipeChoice::class.java,
                    RecipeChoice::class.java,
                    TrimPattern::class.java,
                    Boolean::class.javaPrimitiveType,
                ),
            )
        }.getOrNull()
    }
}
