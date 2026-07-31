package studio.sculk.packets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

@OptIn(SculkInternal::class)
class PacketGuardTest {
    private class Capture : Handler() {
        val records = mutableListOf<LogRecord>()

        override fun publish(record: LogRecord) {
            records += record
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    private val capture = Capture()
    private val logger = Logger.getLogger("guard-${UUID.randomUUID()}").apply {
        useParentHandlers = false
        addHandler(capture)
    }
    private var now = 0L

    private fun guard() = PacketGuard(logger, { now })

    @Test
    fun `a throwing handler does not propagate to the packet pipeline`() {
        // If this escaped, PacketEvents would treat the packet as malformed and kick the player.
        val guard = guard()

        val completed = guard.run("dig") { error("boom") }

        assertFalse(completed)
        assertEquals(1, guard.failures)
    }

    @Test
    fun `a handler that succeeds reports so`() {
        var ran = false

        assertTrue(guard().run("dig") { ran = true })
        assertTrue(ran)
    }

    @Test
    fun `an Error is caught too rather than escaping to the netty thread`() {
        // StackOverflowError from an accidental recursion is a realistic handler bug, and it would
        // reach the pipeline exactly like an exception does.
        assertFalse(guard().run("dig") { throw StackOverflowError("deep") })
    }

    @Test
    fun `repeated failures log one trace per five seconds`() {
        val guard = guard()

        repeat(100) { guard.run("dig") { error("boom") } }

        assertEquals(1, capture.records.size, "100 failures must not write 100 stack traces")
        assertEquals(100, guard.failures, "but every one is still counted")
    }

    @Test
    fun `the log window reopens once the quiet period passes`() {
        val guard = guard()
        guard.run("dig") { error("boom") }
        assertEquals(1, capture.records.size)

        now += 4_999
        guard.run("dig") { error("boom") }
        assertEquals(1, capture.records.size, "still inside the window")

        now += 1
        guard.run("dig") { error("boom") }
        assertEquals(2, capture.records.size)
    }

    @Test
    fun `the log names the consequence rather than only the exception`() {
        guard().run("block use") { error("boom") }

        val message = capture.records.single().message
        assertTrue(message.contains("block use"), message)
        assertTrue(message.contains("kicked"), "the message must explain what this prevented: $message")
        assertTrue(capture.records.single().thrown is IllegalStateException)
    }
}
