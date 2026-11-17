package studio.sculk.pdc

import net.kyori.adventure.key.Key
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import studio.sculk.testkit.paper.FakePersistentDataContainer
import java.util.UUID

class PdcCodecTest {
    private enum class Tier { BRONZE, SILVER, GOLD }

    private val container = FakePersistentDataContainer()

    private fun <T : Any> key(name: String, codec: PdcCodec<T>) = PdcKey(NamespacedKey("test", name), codec)

    @Test
    fun `every built-in codec reads back what it wrote`() {
        val id = UUID.randomUUID()
        val cases = listOf<Pair<PdcKey<*>, Any>>(
            key("text", PdcCodec.STRING) to "hello",
            key("flag", PdcCodec.BOOLEAN) to true,
            key("int", PdcCodec.INT) to 42,
            key("long", PdcCodec.LONG) to 9_000_000_000L,
            key("double", PdcCodec.DOUBLE) to 2.5,
            key("uuid", PdcCodec.UUID) to id,
            key("key", PdcCodec.KEY) to Key.key("shop:ruby"),
            key("tier", PdcCodec.enum<Tier>()) to Tier.GOLD,
            key("names", PdcCodec.STRING.list()) to listOf("a", "b"),
            key("tiers", PdcCodec.enum<Tier>().list()) to listOf(Tier.BRONZE, Tier.SILVER),
        )
        cases.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            container.write(key as PdcKey<Any>, value)
            assertEquals(value, container.read(key).getOrThrow(), "${key.codec}")
        }
    }

    @Test
    fun `an absent key reads as null`() {
        assertNull(container.read(key("missing", PdcCodec.INT)).getOrThrow())
    }

    @Test
    fun `data of another shape is reported, not thrown`() {
        container.setRaw(NamespacedKey("test", "tier"), "PLATINUM")
        container.setRaw(NamespacedKey("test", "count"), "twelve")
        container.setRaw(NamespacedKey("test", "owner"), byteArrayOf(1, 2, 3))
        container.setRaw(NamespacedKey("test", "home"), "Not A Key!")
        val reads = listOf(
            container.read(key("tier", PdcCodec.enum<Tier>())),
            container.read(key("count", PdcCodec.INT)),
            container.read(key("owner", PdcCodec.UUID)),
            container.read(key("home", PdcCodec.KEY)),
        )
        reads.forEach { assertTrue(it is SculkFailure.InvalidInput, "got $it") }
    }

    @Test
    fun `removing a key leaves the others`() {
        val a = key("a", PdcCodec.INT)
        val b = key("b", PdcCodec.INT)
        container.write(a, 1)
        container.write(b, 2)
        container.remove(a)
        assertNull(container.read(a).getOrThrow())
        assertEquals(2, container.read(b).getOrThrow())
    }
}
