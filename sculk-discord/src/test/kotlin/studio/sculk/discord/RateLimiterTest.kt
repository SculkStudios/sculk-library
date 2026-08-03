package studio.sculk.discord

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RateLimiterTest {
    private var now = 0L
    private val limiter = RateLimiter { now }

    @Test
    fun `slots past the limit are refused`() {
        repeat(3) { assertTrue(limiter.acquire(3)) }

        assertFalse(limiter.acquire(3))
    }

    @Test
    fun `a slot frees once it leaves the window`() {
        repeat(3) { limiter.acquire(3) }
        assertFalse(limiter.acquire(3))

        now += 60_001

        assertTrue(limiter.acquire(3))
    }

    @Test
    fun `the window slides rather than resetting, so a steady rate is not batched`() {
        limiter.acquire(2)
        now += 30_000
        limiter.acquire(2)
        assertFalse(limiter.acquire(2))

        // The first slot ages out here; the second has 30s left.
        now += 30_001

        assertTrue(limiter.acquire(2))
        assertFalse(limiter.acquire(2))
    }

    @Test
    fun `a limit of zero still allows one, because refusing everything is never the intent`() {
        assertTrue(limiter.acquire(0))
    }

    @Test
    fun `used reports the current window`() {
        repeat(2) { limiter.acquire(5) }
        assertEquals(2, limiter.used())

        now += 60_001

        assertEquals(0, limiter.used())
    }
}
