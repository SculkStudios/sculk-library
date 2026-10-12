package studio.sculk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class SculkResultTest {
    private val failure = SculkFailure.InvalidInput("config.yml: storage.port", "an integer between 1 and 65535", "90000")

    @Test
    fun `a failure is a result, so matching needs no wrapper`() {
        val result: SculkResult<Int> = failure
        val text = when (result) {
            is SculkResult.Success -> "value ${result.value}"
            is SculkFailure -> result.describe()
        }
        assertEquals("config.yml: storage.port: expected an integer between 1 and 65535, found 90000.", text)
    }

    @Test
    fun `map and flatMap pass failures through untouched`() {
        assertSame(failure, (failure as SculkResult<Int>).map { it + 1 })
        assertSame(failure, (failure as SculkResult<Int>).flatMap { SculkResult.success(it) })
        assertEquals(SculkResult.Success(3), SculkResult.success(2).map { it + 1 })
    }

    @Test
    fun `catching turns an exception into a sentence naming the operation`() {
        val result = SculkResult.catching("read storage.yml") { throw IOException("access denied") }
        val operationFailed = result as SculkFailure.OperationFailed
        assertEquals("Could not read storage.yml: access denied.", operationFailed.message)
        assertTrue(operationFailed.cause is IOException)
    }

    @Test
    fun `catching never swallows cancellation`() {
        assertThrows<CancellationException> { SculkResult.catching("wait") { throw CancellationException("cancelled") } }
    }

    @Test
    fun `getOrThrow carries the failure and its cause`() {
        val io = IOException("disk full")
        val thrown = assertThrows<SculkFailureException> { SculkFailure.OperationFailed("save", io).getOrThrow() }
        assertSame(io, thrown.cause)
        assertTrue(thrown.failure is SculkFailure.OperationFailed)
    }

    @Test
    fun `accessors report the branch they were given`() {
        assertTrue(SculkResult.ok().isSuccess)
        assertFalse(failure.isSuccess)
        assertTrue(failure.isFailure)
        assertNull((failure as SculkResult<Int>).getOrNull())
        assertSame(failure, failure.failureOrNull())
        assertEquals(7, (failure as SculkResult<Int>).getOrElse { 7 })
        assertEquals(SculkResult.Success(0), (failure as SculkResult<Int>).recover { 0 })
    }

    @Test
    fun `onSuccess and onFailure run only on their branch`() {
        var seen = ""
        SculkResult.success("a").onSuccess { seen += it }.onFailure { seen += "!" }
        (failure as SculkResult<String>).onSuccess { seen += it }.onFailure { seen += "b" }
        assertEquals("ab", seen)
    }

    @Test
    fun `a hint follows the message in the full description`() {
        val missing = SculkFailure.MissingIntegration("LuckPerms")
        assertEquals(
            "LuckPerms is not installed or not enabled. Install and enable LuckPerms, then restart the server.",
            missing.describe(),
        )
        assertEquals("Fetching the profile did not complete within 5s.", SculkFailure.Timeout("Fetching the profile", 5.seconds).describe())
    }

    @Test
    fun `the shared ok result is a single instance`() {
        assertSame(SculkResult.ok(), SculkResult.ok())
    }
}
