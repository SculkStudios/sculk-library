package studio.sculk.npc

import org.bukkit.Location
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.SculkFailure
import studio.sculk.capability.Capability
import studio.sculk.paper.PaperCapabilities
import studio.sculk.testkit.FakeRuntime
import studio.sculk.text.Text
import java.lang.reflect.Proxy

/** What `Npcs` decides before it touches a server; `integration-tests/npcs` covers the rest on real ones. */
class NpcsTest {
    // Any call means Npcs reached for the server where it should have stopped first.
    private val plugin = Proxy.newProxyInstance(Plugin::class.java.classLoader, arrayOf(Plugin::class.java)) { _, method, _ ->
        error("Npcs touched the plugin: ${method.name}")
    } as Plugin

    private fun npcs(mannequins: Boolean): Pair<FakeRuntime, Npcs> {
        val state = if (mannequins) Capability.Native(Unit) else Capability.Unavailable("added in Minecraft 1.21.11")
        val runtime = FakeRuntime { resolve(PaperCapabilities.MANNEQUINS, state) }
        runtime.start()
        return runtime to Npcs(runtime, plugin, Text()) { error("no adapter is loaded before a spawn reaches the world") }
    }

    @Test
    fun `without mannequins a spawn fails with UnsupportedCapability and touches nothing`() {
        val (runtime, npcs) = npcs(mannequins = false)
        assertFalse(npcs.isAvailable)
        val result = runtime.executor.run { npcs.spawn(Location(null, 0.0, 64.0, 0.0), npc { name("Guide") }) }
        val failure = result as SculkFailure.UnsupportedCapability
        assertEquals("NPCs", failure.capability)
        assertEquals("added in Minecraft 1.21.11", failure.reason)
        assertTrue(npcs.spawned.isEmpty())
    }

    @Test
    fun `a location without a world is a programming error`() {
        val (runtime, npcs) = npcs(mannequins = true)
        assertTrue(npcs.isAvailable)
        assertThrows<IllegalArgumentException> { runtime.executor.run { npcs.spawn(Location(null, 0.0, 64.0, 0.0), npc {}) } }
    }

    @Test
    fun `closing the runtime with nothing spawned registers and removes nothing`() {
        val (runtime, npcs) = npcs(mannequins = true)
        runtime.close()
        assertTrue(npcs.spawned.isEmpty())
        assertTrue(runtime.logged.errors.isEmpty(), "${runtime.logged.errors}")
    }
}
