package studio.sculk.recipe

import net.kyori.adventure.key.Key
import org.bukkit.Material
import studio.sculk.annotation.SculkStable
import studio.sculk.item.ItemSpec

/** What a recipe slot accepts. */
@SculkStable
public sealed class Ingredient {
    /** Any of [materials], whatever their components. */
    @SculkStable
    public class Items(materials: Set<Material>) : Ingredient() {
        public val materials: Set<Material> = materials.toSet()

        init {
            require(materials.isNotEmpty()) { "an ingredient accepts at least one material" }
        }

        override fun equals(other: Any?): Boolean = other is Items && other.materials == materials

        override fun hashCode(): Int = materials.hashCode()

        override fun toString(): String = materials.joinToString(" or ") { it.name }
    }

    /** Any item in the item tag [key], such as `minecraft:planks`. */
    @SculkStable
    public class Tag(public val key: Key) : Ingredient() {
        override fun equals(other: Any?): Boolean = other is Tag && other.key == key

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String = "#${key.asString()}"
    }

    /** Exactly a stack of one of [specs], components included: a custom item rather than any item of its type. */
    @SculkStable
    public class Exact(specs: List<ItemSpec>) : Ingredient() {
        public val specs: List<ItemSpec> = specs.toList()

        init {
            require(specs.isNotEmpty()) { "an exact ingredient names at least one item" }
        }

        override fun equals(other: Any?): Boolean = other is Exact && other.specs == specs

        override fun hashCode(): Int = specs.hashCode()

        override fun toString(): String = "exactly ${specs.joinToString(" or ")}"
    }

    @SculkStable
    public companion object {
        /** Any item of one of [materials], at least one, whatever its components; see [Items]. */
        public fun of(vararg materials: Material): Ingredient = Items(materials.toSet())

        /** Any item in the item tag [key]; [Recipes.register] fails when the server has no such tag. See [Tag]. */
        public fun tag(key: Key): Ingredient = Tag(key)

        /** Exactly a stack of one of [specs], at least one, components included; see [Exact]. */
        public fun exact(vararg specs: ItemSpec): Ingredient = Exact(specs.toList())
    }
}
