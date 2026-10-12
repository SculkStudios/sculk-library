package studio.sculk.testkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.capability.Capability
import studio.sculk.capability.CapabilityKey
import studio.sculk.execution.withEntity
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.runtime.SculkModule
import studio.sculk.runtime.SculkRuntime

class FakeRuntimeTest {
    @Test
    fun `starting walks every phase to READY and fixes the capabilities`() {
        val key = CapabilityKey<String>("test.feature", "Test feature")
        val runtime = FakeRuntime { resolve(key, Capability.Native("on")) }
        val seen = mutableListOf<LifecyclePhase>()
        LifecyclePhase.entries.filter { it in LifecyclePhase.CONFIGURE..LifecyclePhase.READY }.forEach { phase ->
            runtime.lifecycle.on(phase) { seen += phase }
        }
        runtime.start()
        assertEquals(listOf(LifecyclePhase.CONFIGURE, LifecyclePhase.LOAD, LifecyclePhase.START, LifecyclePhase.READY), seen)
        assertEquals("native", runtime.capabilities[key].level)
    }

    @Test
    fun `a module installs once`() {
        val runtime = FakeRuntime()
        val module = object : SculkModule {
            override val id = "test-module"
            var installs = 0

            override fun install(runtime: SculkRuntime) {
                installs++
            }
        }
        runtime.install(module)
        assertThrows<IllegalStateException> { runtime.install(module) }
        assertEquals(1, module.installs)
    }

    @Test
    fun `the platform decides how ownership behaves`() {
        val zombie = FakeEntity("Zombie")
        val folia = FakeRuntime(platform = FakeRuntime.folia())
        val result = folia.executor.run { withEntity(zombie) { folia.executor.owns(studio.sculk.execution.ExecutionContext.Global) } }
        assertEquals(false, (result as studio.sculk.execution.EntityResult.Ran).value)
    }

    @Test
    fun `a failing coroutine is logged against the plugin, and closing reports instead of throwing`() {
        val runtime = FakeRuntime(name = "Shop")
        runtime.start()
        runtime.scope.launch { error("broken") }
        runtime.executor.tick()
        runtime.scope.resources.onClose { error("close failed") }
        runtime.close()
        val errors = runtime.logged.errors.map { it.message }
        assertEquals(listOf("A coroutine in Shop failed.", "Closing Shop's resources failed; shutdown continued."), errors)
        assertEquals(LifecyclePhase.STOPPED, runtime.lifecycle.phase)
    }

    @Test
    fun `stopping a runtime whose start failed runs its shutdown actions but none of its startup actions`() {
        val runtime = FakeRuntime()
        val seen = mutableListOf<String>()
        runtime.lifecycle.on(LifecyclePhase.START) { seen += "register commands" }
        runtime.lifecycle.on(LifecyclePhase.STOPPING) { seen += "save data" }
        runtime.lifecycle.advance(LifecyclePhase.CONFIGURE)
        runtime.close()
        runtime.close()
        assertEquals(listOf("save data"), seen)
        assertEquals(LifecyclePhase.STOPPED, runtime.lifecycle.phase)
    }

    @Test
    fun `a failing shutdown action is logged and the scope still closes`() {
        val runtime = FakeRuntime()
        runtime.start()
        runtime.lifecycle.on(LifecyclePhase.STOPPING) { error("save failed") }
        runtime.close()
        assertTrue(runtime.scope.isClosed)
        assertEquals(listOf("Running TestPlugin's shutdown actions failed; shutdown continued."), runtime.logged.errors.map { it.message })
    }

    @Test
    fun `a module whose install fails can be installed again once fixed`() {
        val runtime = FakeRuntime()
        var broken = true
        val module = object : SculkModule {
            override val id = "flaky"

            override fun install(runtime: SculkRuntime) {
                check(!broken) { "not yet" }
            }
        }
        assertThrows<IllegalStateException> { runtime.install(module) }
        broken = false
        runtime.install(module)
        assertTrue("flaky" in runtime.diagnostics().render())
    }

    @Test
    fun `diagnostics describe the runtime`() {
        val runtime = FakeRuntime(platform = FakeRuntime.canvas())
        runtime.start()
        val text = runtime.diagnostics().render()
        assertTrue("Platform            CANVAS (Fake Canvas)" in text, text)
        assertTrue("Minecraft           26.3" in text, text)
    }
}
