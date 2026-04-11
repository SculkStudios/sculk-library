package gg.sculk.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SculkResultTest {
    @Test
    fun `success holds value`() {
        val result = SculkResult.success("hello")
        assertTrue(result.isSuccess)
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `failure holds message`() {
        val result = SculkResult.failure("something went wrong")
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrDefault returns value on success`() {
        val result = SculkResult.success(42)
        assertEquals(42, result.getOrDefault(0))
    }

    @Test
    fun `getOrDefault returns default on failure`() {
        val result: SculkResult<Int> = SculkResult.failure("oops")
        assertEquals(0, result.getOrDefault(0))
    }

    @Test
    fun `failure carries optional cause`() {
        val cause = RuntimeException("root cause")
        val result = SculkResult.failure("failed", cause)
        assertEquals(cause, (result as SculkResult.Failure).cause)
    }
}
