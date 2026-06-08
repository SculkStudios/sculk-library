package studio.sculk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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

    // --- Java-facing member methods (must be safe on the Failure : SculkResult<Nothing> arm) ---

    @Test
    fun `member isSuccess and isFailure`() {
        assertTrue(SculkResult.success(1).isSuccess())
        assertFalse(SculkResult.success(1).isFailure())
        val failure: SculkResult<Int> = SculkResult.failure("x")
        assertTrue(failure.isFailure())
        assertFalse(failure.isSuccess())
    }

    @Test
    fun `member getOrNull on typed failure returns null`() {
        val failure: SculkResult<Int> = SculkResult.failure("x")
        assertNull(failure.getOrNull())
        assertEquals(7, SculkResult.success(7).getOrNull())
    }

    @Test
    fun `getOrThrow returns value on success`() {
        assertEquals(42, SculkResult.success(42).getOrThrow())
    }

    @Test
    fun `getOrThrow throws on typed failure`() {
        val failure: SculkResult<Int> = SculkResult.failure("boom")
        assertThrows(IllegalStateException::class.java) { failure.getOrThrow() }
    }

    @Test
    fun `ifSuccess runs only on success and returns this`() {
        var seen: Int? = null
        val success = SculkResult.success(5)
        val returned = success.ifSuccess { seen = it }
        assertEquals(5, seen)
        assertEquals(success, returned)

        seen = null
        val failure: SculkResult<Int> = SculkResult.failure("x")
        failure.ifSuccess { seen = it }
        assertNull(seen)
    }

    @Test
    fun `ifFailure runs only on failure with message and cause`() {
        var message: String? = null
        val cause = RuntimeException("root")
        val failure: SculkResult<Int> = SculkResult.failure("nope", cause)
        val returned =
            failure.ifFailure { msg, err ->
                message = msg
                assertEquals(cause, err)
            }
        assertEquals("nope", message)
        assertEquals(failure, returned)

        message = null
        SculkResult.success(1).ifFailure { msg, _ -> message = msg }
        assertNull(message)
    }
}
