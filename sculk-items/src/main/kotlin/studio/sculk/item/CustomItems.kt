package studio.sculk.item

import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import studio.sculk.ResourceScope
import studio.sculk.SculkFailure
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.getOrNull
import studio.sculk.map
import studio.sculk.pdc.read
import studio.sculk.text.Text
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A source of custom items another plugin defines, such as Nexo, Oraxen or ItemsAdder. Ids are
 * namespaced by [namespace] (`nexo:ruby_sword`).
 */
@SculkStable
public interface CustomItemProvider {
    /** The namespace this provider's ids use. */
    public val namespace: String

    /** A new stack of the item [id], or null when the provider has no such item. Any thread. */
    public fun create(id: Key, amount: Int): ItemStack?

    /** The id of the custom item [stack] is, or null when it is not one of this provider's. Any thread. */
    public fun identify(stack: ItemStack): Key?
}

/**
 * Every custom item a plugin knows: its own [ItemSpec]s with an [ItemSpec.id], and those of the
 * registered [CustomItemProvider]s. Thread-safe.
 *
 * ```kotlin
 * val items = CustomItems()
 * items.define(sculk.scope, blade)                 // blade.id = shop:sculk_blade
 * val stack = items.create(Key.key("shop:sculk_blade")).getOrThrow()
 * items.identify(stack)                              // shop:sculk_blade
 * ```
 */
@SculkStable
public class CustomItems {
    private val specs = ConcurrentHashMap<Key, ItemSpec>()
    private val providers = CopyOnWriteArrayList<CustomItemProvider>()

    /** Makes [spec] available by its id until [scope] closes. A second definition of one id is a wiring bug and throws. */
    public fun define(scope: ResourceScope, spec: ItemSpec): SculkHandle {
        val id = requireNotNull(spec.id) { "a custom item needs an id; set ItemSpec.id" }
        check(specs.putIfAbsent(id, spec) == null) { "custom item $id is defined twice" }
        return scope.handle { specs.remove(id, spec) }
    }

    /** Adds [provider] until [scope] closes. */
    public fun register(scope: ResourceScope, provider: CustomItemProvider): SculkHandle {
        providers += provider
        return scope.handle { providers.remove(provider) }
    }

    /** The spec defined as [id], if any. */
    public fun spec(id: Key): ItemSpec? = specs[id]

    /**
     * A new stack of [amount] of [id], from a defined spec, its name and lore rendered by [text], or
     * from the provider of its namespace, which renders its own. An [SculkFailure.InvalidInput] when
     * none knows the id, the amount is outside 1 to the item's stack size, or the server cannot build
     * the spec (see [ItemSpec.check]), as all of these may come from configuration. Any thread.
     */
    public fun create(id: Key, amount: Int = 1, text: Text = Text()): SculkResult<ItemStack> {
        if (amount !in 1..ItemSpec.MAX_STACK) return invalidAmount(id, amount, ItemSpec.MAX_STACK)
        specs[id]?.let { spec ->
            val most = spec.maxStackSize ?: ItemSpec.MAX_STACK
            if (amount > most) return invalidAmount(id, amount, most)
            val sized = if (spec.amount == amount) spec else spec.with { this.amount = amount }
            return sized.check().map { it.create(text) }
        }
        val stack = providers.filter { it.namespace == id.namespace() }.firstNotNullOfOrNull { it.create(id, amount) }
        return stack?.let { SculkResult.success(it) }
            ?: SculkFailure.InvalidInput("custom item", "a defined or provided item id", id.asString())
    }

    private fun invalidAmount(id: Key, amount: Int, most: Int): SculkFailure =
        SculkFailure.InvalidInput("amount of ${id.asString()}", "1 to $most", amount.toString())

    /** The custom item id of [stack]: Sculk's own mark first, then each provider in registration order. */
    public fun identify(stack: ItemStack): Key? {
        if (stack.isEmpty) return null
        stack.read(ItemCompiler.ID).getOrNull()?.let { return it }
        return providers.firstNotNullOfOrNull { it.identify(stack) }
    }

    private fun ResourceScope.handle(remove: () -> Unit): SculkHandle {
        val removal = SculkHandle.once(remove)
        val owned = onClose(removal::close)
        return SculkHandle.once {
            owned.close()
            removal.close()
        }
    }
}
