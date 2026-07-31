package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CommandCooldownsTest {
    private var now = 0L
    private val cooldowns = CommandCooldowns { now }
    private val window = Duration.ofSeconds(5)

    @Test
    fun `the first use is allowed and the second is not`() {
        assertTrue(cooldowns.tryUse("kit", "player", window))
        assertFalse(cooldowns.tryUse("kit", "player", window))
    }

    @Test
    fun `the cooldown expires once the window has passed`() {
        cooldowns.tryUse("kit", "player", window)

        now += 4_999
        assertFalse(cooldowns.tryUse("kit", "player", window), "one millisecond short is still short")

        now += 1
        assertTrue(cooldowns.tryUse("kit", "player", window))
    }

    @Test
    fun `senders are tracked separately`() {
        assertTrue(cooldowns.tryUse("kit", "one", window))
        assertTrue(cooldowns.tryUse("kit", "two", window))
    }

    @Test
    fun `commands are tracked separately`() {
        assertTrue(cooldowns.tryUse("kit", "player", window))
        assertTrue(cooldowns.tryUse("warp", "player", window))
    }

    @Test
    fun `remaining counts down and reaches zero`() {
        cooldowns.tryUse("kit", "player", window)

        assertEquals(5_000, cooldowns.remaining("kit", "player", window).toMillis())

        now += 3_000
        assertEquals(2_000, cooldowns.remaining("kit", "player", window).toMillis())

        now += 2_000
        assertEquals(Duration.ZERO, cooldowns.remaining("kit", "player", window))
    }

    @Test
    fun `forget drops a sender without touching the others`() {
        cooldowns.tryUse("kit", "leaver", window)
        cooldowns.tryUse("kit", "stayer", window)

        cooldowns.forget("leaver")

        assertTrue(cooldowns.tryUse("kit", "leaver", window))
        assertFalse(cooldowns.tryUse("kit", "stayer", window))
    }

    @Test
    fun `concurrent uses in the same instant record exactly one`() {
        // The reason check-and-record is a single compute: separated, every thread here reads the
        // absent entry before any of them writes it, and all sixteen pass.
        val threads = 16
        val allowed = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            pool.submit {
                start.await()
                if (cooldowns.tryUse("kit", "player", window)) allowed.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()
        done.await(5, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertEquals(1, allowed.get())
    }
}
