package studio.sculk.platform

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit

class SculkPluginTest {
    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `base class wires platform lifecycle and setup hook`() {
        val plugin = MockBukkit.load(TestSculkPlugin::class.java)

        assertTrue(plugin.setupRan, "setup() should run after enable")
        assertTrue(plugin.platformReady, "sculk should be initialized inside setup()")

        MockBukkit.unmock() // triggers plugin disable

        assertTrue(plugin.shutdownRan, "shutdown() should run on disable")

        // Re-mock so @AfterEach's unmock is safe.
        MockBukkit.mock()
        assertFalse(plugin.isEnabled)
    }

    open class TestSculkPlugin : SculkPlugin({ gui() }) {
        var setupRan = false
        var shutdownRan = false
        var platformReady = false

        override fun setup() {
            setupRan = true
            // Accessing sculk here proves it was initialized before setup() ran.
            platformReady = runCatching { sculk.scheduler }.isSuccess
        }

        override fun shutdown() {
            shutdownRan = true
        }
    }
}
