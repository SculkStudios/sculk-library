package studio.sculk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.diagnostics.DiagnosticReport
import studio.sculk.lifecycle.Lifecycle
import studio.sculk.lifecycle.LifecyclePhase
import studio.sculk.service.ServiceRegistry
import studio.sculk.service.find
import studio.sculk.service.register
import studio.sculk.service.require

class CoreSupportTest {
    @Test
    fun `a secret never prints its value`() {
        val password = Secret("hunter2")
        assertEquals("<redacted>", password.toString())
        assertEquals("password=<redacted>", "password=$password")
        assertEquals("hunter2", password.reveal())
    }

    @Test
    fun `lifecycle actions run when their phase is entered, in registration order`() {
        val lifecycle = Lifecycle()
        val seen = mutableListOf<String>()
        lifecycle.on(LifecyclePhase.START) { seen += "commands" }
        lifecycle.on(LifecyclePhase.START) { seen += "listeners" }
        lifecycle.on(LifecyclePhase.READY) { seen += "ready" }
        lifecycle.advance(LifecyclePhase.CONFIGURE)
        assertTrue(seen.isEmpty())
        lifecycle.advance(LifecyclePhase.START)
        assertEquals(listOf("commands", "listeners"), seen)
        assertTrue(lifecycle.hasReached(LifecyclePhase.LOAD))
        assertFalse(lifecycle.hasReached(LifecyclePhase.READY))
    }

    @Test
    fun `a lifecycle never goes backwards and rejects actions for phases already entered`() {
        val lifecycle = Lifecycle(LifecyclePhase.READY)
        assertThrows<IllegalStateException> { lifecycle.advance(LifecyclePhase.START) }
        assertThrows<IllegalStateException> { lifecycle.on(LifecyclePhase.READY) {} }
        lifecycle.on(LifecyclePhase.STOPPING) {}
    }

    @Test
    fun `services are found by type and registered once`() {
        val services = ServiceRegistry()
        val economy = StringBuilder("economy")
        services.register(economy)
        assertSame(economy, services.require<StringBuilder>())
        assertNull(services.find<Runnable>())
        assertThrows<IllegalStateException> { services.register(StringBuilder()) }
        val missing = assertThrows<IllegalStateException> { services.require<Runnable>() }
        assertEquals("No Runnable is registered.", missing.message)
    }

    @Test
    fun `a diagnostic report aligns its entries per section`() {
        val report = DiagnosticReport.build {
            section("Platform") {
                entry("Server", "Paper 26.3")
                entry("Java", 25)
            }
        }
        assertEquals("Platform\n  Server  Paper 26.3\n  Java    25\n", report.render())
    }
}
