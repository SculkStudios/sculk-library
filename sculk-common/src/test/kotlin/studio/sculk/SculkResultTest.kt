package studio.sculk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SculkResultTest {
    @Test
    fun `catching turns a thrown exception into a failure describing the action`() {
        val result = SculkResult.catching("read tools.yml") { error("file is locked") }

        val failure = result as SculkResult.Failure
        assertEquals("Failed to read tools.yml: file is locked", failure.message)
        assertTrue(failure.cause is IllegalStateException)
    }

    @Test
    fun `catching describes the exception type when it carries no message`() {
        val result = SculkResult.catching("parse the id") { throw NullPointerException() }

        assertEquals("Failed to parse the id: NullPointerException", (result as SculkResult.Failure).message)
    }

    @Test
    fun `catching passes a successful value straight through`() {
        assertEquals(7, SculkResult.catching("count rows") { 7 }.getOrNull())
    }

    @Test
    fun `ok returns the same instance every time`() {
        assertSame(SculkResult.ok(), SculkResult.ok())
    }

    @Test
    fun `map is not invoked on a failure`() {
        var invoked = false

        val result: SculkResult<Int> = SculkResult.failure("no row")
        val mapped = result.map {
            invoked = true
            it * 2
        }

        assertFalse(invoked, "map must not run its transform on a failure")
        assertEquals("no row", (mapped as SculkResult.Failure).message)
    }

    @Test
    fun `flatMap short-circuits on the first failure`() {
        val result: SculkResult<Int> = SculkResult
            .success(1)
            .flatMap { SculkResult.failure("stopped here") }
            .flatMap { error("must not be reached") }

        assertEquals("stopped here", (result as SculkResult.Failure).message)
    }

    @Test
    fun `getOrElse computes the fallback from the failure message`() {
        val result: SculkResult<String> = SculkResult.failure("absent")

        assertEquals("fallback for absent", result.getOrElse { message, _ -> "fallback for $message" })
    }

    @Test
    fun `recover replaces a failure with a success`() {
        val recovered: SculkResult<Int> = SculkResult.failure("no row")

        val value = recovered.recover { _, _ -> 0 }

        assertTrue(value.isSuccess)
        assertEquals(0, value.getOrNull())
    }

    @Test
    fun `fold collapses both arms`() {
        assertEquals("ok:3", SculkResult.success(3).fold({ "ok:$it" }, { message, _ -> "err:$message" }))
        assertEquals("err:gone", SculkResult.failure("gone").fold({ "ok:$it" }, { message, _ -> "err:$message" }))
    }

    @Test
    fun `getOrNull is null on a failure`() {
        assertNull(SculkResult.failure("gone").getOrNull())
    }

    @Test
    fun `onSuccess and onFailure return the receiver so they can be chained`() {
        val seen = mutableListOf<String>()

        val result = SculkResult
            .success("value")
            .onSuccess { seen += "success" }
            .onFailure { _, _ -> seen += "failure" }

        assertEquals(listOf("success"), seen)
        assertEquals("value", result.getOrNull())
    }
}
