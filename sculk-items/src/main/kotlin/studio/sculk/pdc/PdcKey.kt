package studio.sculk.pdc

import io.papermc.paper.persistence.PersistentDataContainerView
import io.papermc.paper.persistence.PersistentDataViewHolder
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataHolder
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * A typed persistent data entry: where it is stored and how. Declare each once, as a top-level
 * value, and read and write it on any holder.
 *
 * ```kotlin
 * val OWNER = PdcKey(NamespacedKey("shop", "owner"), PdcCodec.UUID)
 * stack.write(OWNER, player.uniqueId)
 * val owner: UUID? = stack.read(OWNER).getOrNull()
 * ```
 */
@SculkStable
public class PdcKey<T : Any>(public val key: NamespacedKey, public val codec: PdcCodec<T>) {
    override fun equals(other: Any?): Boolean = other is PdcKey<*> && other.key == key && other.codec == codec

    override fun hashCode(): Int = 31 * key.hashCode() + codec.hashCode()

    override fun toString(): String = "PdcKey($key, ${codec.description})"
}

/** A key and the value to store under it, as an item spec carries it. */
@SculkStable
public class PdcValue<T : Any>(public val key: PdcKey<T>, public val value: T) {
    internal fun writeTo(container: PersistentDataContainer) {
        container.write(key, value)
    }

    override fun equals(other: Any?): Boolean = other is PdcValue<*> && other.key == key && valueEquals(other.value)

    private fun valueEquals(other: Any): Boolean = if (value is ByteArray &&
        other is ByteArray
    ) {
        value.contentEquals(other)
    } else {
        value == other
    }

    override fun hashCode(): Int = 31 * key.hashCode() + if (value is ByteArray) value.contentHashCode() else value.hashCode()

    override fun toString(): String = "PdcValue(${key.key}=$value)"
}

/**
 * The value stored under [key]: null when nothing is, or [SculkFailure.InvalidInput] when what is
 * stored is not a valid [T], such as data an older plugin version wrote in another shape.
 */
@SculkStable
public fun <T : Any> PersistentDataContainerView.read(key: PdcKey<T>): SculkResult<T?> = try {
    SculkResult.success(get(key.key, key.codec.type))
} catch (invalid: RuntimeException) {
    // Paper throws IllegalArgumentException for a stored primitive of another type; a mapped
    // codec's decode throws whatever its parser throws (InvalidKeyException for a bad key).
    SculkFailure.InvalidInput(key.key.asString(), key.codec.description, invalid.message ?: "a value of another type")
}

/** Stores [value] under [key], replacing whatever was there. */
@SculkStable
public fun <T : Any> PersistentDataContainer.write(key: PdcKey<T>, value: T) {
    set(key.key, key.codec.type, value)
}

/** Removes [key]; nothing happens when it is absent. */
@SculkStable
public fun PersistentDataContainer.remove(key: PdcKey<*>) {
    remove(key.key)
}

/**
 * Reads [key] from this holder: an item stack, an entity, a tile state, a chunk or a world. An
 * entity, block or chunk is read on the thread that owns it (its region on Folia and Canvas).
 */
@SculkStable
public fun <T : Any> PersistentDataViewHolder.read(key: PdcKey<T>): SculkResult<T?> = persistentDataContainer.read(key)

/**
 * Stores [value] under [key] on an entity, tile state, chunk or world, on the thread that owns it.
 * A tile state is a snapshot: call `update()` afterwards for the block to keep the value.
 */
@SculkStable
public fun <T : Any> PersistentDataHolder.write(key: PdcKey<T>, value: T) {
    persistentDataContainer.write(key, value)
}

/** Removes [key] from an entity, tile state, chunk or world, on the thread that owns it. */
@SculkStable
public fun PersistentDataHolder.remove(key: PdcKey<*>) {
    persistentDataContainer.remove(key)
}

/** Stores [value] under [key] on this stack. A stack is not thread-safe; share none while writing. */
@SculkStable
public fun <T : Any> ItemStack.write(key: PdcKey<T>, value: T) {
    editPersistentDataContainer { it.write(key, value) }
}

/** Removes [key] from this stack. */
@SculkStable
public fun ItemStack.remove(key: PdcKey<*>) {
    editPersistentDataContainer { it.remove(key) }
}
