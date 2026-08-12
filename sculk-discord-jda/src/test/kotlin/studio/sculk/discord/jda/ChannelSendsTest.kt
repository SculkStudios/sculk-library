package studio.sculk.discord.jda

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.SculkResult
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ChannelSendsTest {
    private val sends = ChannelSends()

    @Test
    fun `a send that works is not retried`() = runTest {
        val calls = AtomicInteger()

        val result = sends.ordered("chat", "send") { calls.incrementAndGet() }

        assertEquals(1, calls.get())
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `a network failure is retried once`() = runTest {
        val calls = AtomicInteger()

        val result = sends.ordered("chat", "send") {
            if (calls.incrementAndGet() == 1) throw IOException("connection reset") else "sent"
        }

        assertEquals(2, calls.get())
        assertEquals("sent", result.getOrNull())
    }

    @Test
    fun `an unrecognised failure is not retried, so a permissions mistake is not a request storm`() = runTest {
        val calls = AtomicInteger()

        val result = sends.ordered("chat", "send") {
            calls.incrementAndGet()
            error("missing permission")
        }

        assertEquals(1, calls.get())
        assertTrue(result is SculkResult.Failure)
    }

    @Test
    fun `a failure that survives the retry reports that it was retried`() = runTest {
        val result = sends.ordered("chat", "send") { throw IOException("still down") }

        val message = (result as SculkResult.Failure).message
        assertTrue(message.contains("after a retry"), message)
    }

    @Test
    fun `sends to one channel run one at a time, so relayed chat keeps its order`() = runTest {
        val running = AtomicInteger()
        val overlapped = AtomicInteger()

        coroutineScope {
            (1..8).map {
                async {
                    sends.ordered("chat", "send") {
                        if (running.incrementAndGet() > 1) overlapped.incrementAndGet()
                        delay(1)
                        running.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals(0, overlapped.get())
    }

    @Test
    fun `a channel churning through senders keeps one queue, not one per arrival`() = runTest {
        val running = AtomicInteger()
        val overlapped = AtomicInteger()

        // Staggered starts, so senders arrive while earlier ones are finishing — the window in which a
        // slot could be dropped and rebuilt, leaving two mutexes guarding the same channel.
        coroutineScope {
            (1..24).map { index ->
                async {
                    delay(index.toLong())
                    sends.ordered("chat", "send") {
                        if (running.incrementAndGet() > 1) overlapped.incrementAndGet()
                        delay(2)
                        running.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals(0, overlapped.get())
    }

    @Test
    fun `different channels do not wait on each other`() = runTest {
        val order = mutableListOf<String>()

        coroutineScope {
            val slow = async {
                sends.ordered("console", "send") {
                    delay(50)
                    synchronized(order) { order += "console" }
                }
            }
            val quick = async {
                delay(1)
                sends.ordered("chat", "send") { synchronized(order) { order += "chat" } }
            }
            awaitAll(slow, quick)
        }

        assertEquals(listOf("chat", "console"), order)
    }
}
