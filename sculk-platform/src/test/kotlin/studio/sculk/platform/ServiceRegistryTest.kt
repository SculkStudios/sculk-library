package studio.sculk.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkHandle

class ServiceRegistryTest {
    private class Economy
    private class Shops

    private open class Closeable(val name: String, val log: MutableList<String>) : SculkHandle {
        override fun close() {
            log += name
        }
    }

    // Distinct types, because the registry is keyed by type and refuses a repeat by design.
    private class FirstCloseable(log: MutableList<String>) : Closeable("first", log)

    private class SecondCloseable(log: MutableList<String>) : Closeable("second", log)

    private class Thrower : SculkHandle {
        override fun close(): Unit = error("boom")
    }

    private val registry = ServiceRegistry()

    @Test
    fun `a registered service is returned by type`() {
        val economy = Economy()

        registry.register(economy)

        assertSame(economy, registry.get<Economy>())
        assertSame(economy, registry.find<Economy>())
        assertTrue(registry.has<Economy>())
    }

    @Test
    fun `an unregistered type is null from find and loud from get`() {
        assertNull(registry.find<Economy>())
        assertFalse(registry.has<Economy>())

        val failure = assertThrows(IllegalStateException::class.java) { registry.get<Economy>() }

        assertTrue(failure.message!!.contains("Economy"), failure.message)
    }

    @Test
    fun `the failure names what was registered instead`() {
        registry.register(Shops())

        val failure = assertThrows(IllegalStateException::class.java) { registry.get<Economy>() }

        assertTrue(failure.message!!.contains("Shops"), "the message should help: ${failure.message}")
    }

    @Test
    fun `registering the same type twice fails loudly`() {
        registry.register(Economy())

        // Silently replacing would leave half the plugin holding the previous instance, which is
        // close to impossible to diagnose from the symptoms.
        val failure = assertThrows(IllegalArgumentException::class.java) { registry.register(Economy()) }

        assertTrue(failure.message!!.contains("already registered"), failure.message)
    }

    @Test
    fun `two different types coexist`() {
        val economy = Economy()
        val shops = Shops()

        registry.register(economy)
        registry.register(shops)

        assertSame(economy, registry.get<Economy>())
        assertSame(shops, registry.get<Shops>())
    }

    @Test
    fun `services close in reverse registration order`() {
        val log = mutableListOf<String>()
        registry.register(FirstCloseable(log))
        registry.register(Shops())
        registry.register(Economy())

        registry.close()

        // Only the closeable one is closed; the plain services are simply dropped.
        assertEquals(listOf("first"), log)
    }

    @Test
    fun `every closeable service is closed, newest first`() {
        val log = mutableListOf<String>()
        registry.register(FirstCloseable(log))
        registry.register(SecondCloseable(log))

        registry.close()

        // Registration order is dependency order: whatever was registered last is most likely to
        // still hold a reference to something registered earlier.
        assertEquals(listOf("second", "first"), log)
    }

    @Test
    fun `a service that throws on close does not strand the others`() {
        val log = mutableListOf<String>()
        registry.register(FirstCloseable(log))
        registry.register(Thrower())

        assertThrows(IllegalStateException::class.java) { registry.close() }

        assertEquals(listOf("first"), log, "the throwing service must not prevent the rest closing")
    }

    @Test
    fun `closing empties the registry`() {
        registry.register(Economy())

        registry.close()

        assertFalse(registry.has<Economy>())
        assertEquals(emptyList<Any>(), registry.registered)
    }

    @Test
    fun `registration order is reported`() {
        registry.register(Economy())
        registry.register(Shops())

        assertEquals(listOf("Economy", "Shops"), registry.registered.map { it.simpleName })
    }
}
