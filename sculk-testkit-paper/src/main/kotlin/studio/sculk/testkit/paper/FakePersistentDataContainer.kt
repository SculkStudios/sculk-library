package studio.sculk.testkit.paper

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import studio.sculk.annotation.SculkStable

/**
 * A persistent data container in memory, for testing code that reads and writes persistent data
 * without a server. It stores what a [PersistentDataType] turns a value into, as Paper does, and
 * refuses a read whose type stores another primitive, as Paper does.
 *
 * It cannot model NBT, so [serializeToBytes] and [readFromBytes] throw; test byte round trips on
 * a real server.
 */
@SculkStable
public class FakePersistentDataContainer : PersistentDataContainer {
    private val values = LinkedHashMap<NamespacedKey, Any>()
    private val context = object : PersistentDataAdapterContext {
        override fun newPersistentDataContainer(): PersistentDataContainer = FakePersistentDataContainer()
    }

    /** Stores [primitive] under [key] as it is, for tests of data another plugin or version wrote. */
    public fun setRaw(key: NamespacedKey, primitive: Any) {
        values[key] = primitive
    }

    override fun <P, C> set(key: NamespacedKey, type: PersistentDataType<P, C>, value: C & Any) {
        values[key] = requireNotNull(type.toPrimitive(value, context)) { "$type turned $value into null" }
    }

    override fun remove(key: NamespacedKey) {
        values.remove(key)
    }

    override fun <P : Any, C : Any> has(key: NamespacedKey, type: PersistentDataType<P, C>): Boolean =
        values[key]?.let { type.primitiveType.isInstance(it) } ?: false

    override fun has(key: NamespacedKey): Boolean = key in values

    override fun <P : Any, C : Any> get(key: NamespacedKey, type: PersistentDataType<P, C>): C? {
        val stored = values[key] ?: return null
        require(type.primitiveType.isInstance(stored)) {
            "The found tag instance (${stored::class.java.simpleName}) cannot store ${type.primitiveType.simpleName}"
        }
        return type.fromPrimitive(type.primitiveType.cast(stored), context)
    }

    override fun <P : Any, C : Any> getOrDefault(key: NamespacedKey, type: PersistentDataType<P, C>, defaultValue: C): C =
        get(key, type) ?: defaultValue

    override fun getKeys(): Set<NamespacedKey> = values.keys.toSet()

    override fun isEmpty(): Boolean = values.isEmpty()

    override fun copyTo(other: PersistentDataContainer, replace: Boolean) {
        require(other is FakePersistentDataContainer) { "a fake container copies only into another fake container" }
        values.forEach { (key, value) -> if (replace || key !in other.values) other.values[key] = value }
    }

    override fun getAdapterContext(): PersistentDataAdapterContext = context

    override fun serializeToBytes(): ByteArray =
        throw UnsupportedOperationException("The fake container cannot write NBT; test byte round trips on a server.")

    override fun readFromBytes(bytes: ByteArray, clear: Boolean): Unit =
        throw UnsupportedOperationException("The fake container cannot read NBT; test byte round trips on a server.")

    override fun toString(): String = "FakePersistentDataContainer($values)"
}
