package studio.sculk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SculkHandleTest {
    @Test
    fun `all closes handles in reverse registration order`() {
        val closed = mutableListOf<String>()

        SculkHandle.all(
            SculkHandle { closed += "first" },
            SculkHandle { closed += "second" },
            SculkHandle { closed += "third" },
        ).close()

        assertEquals(listOf("third", "second", "first"), closed)
    }

    @Test
    fun `a throwing handle does not prevent the remaining handles from closing`() {
        val closed = mutableListOf<String>()

        val all = SculkHandle.all(
            SculkHandle { closed += "first" },
            SculkHandle { throw IllegalStateException("boom") },
            SculkHandle { closed += "third" },
        )

        assertThrows(IllegalStateException::class.java) { all.close() }
        assertEquals(listOf("third", "first"), closed, "a bad close must not strand the handles behind it")
    }

    @Test
    fun `the first failure is rethrown with the others attached as suppressed`() {
        val all = SculkHandle.all(
            SculkHandle { throw IllegalStateException("earlier") },
            SculkHandle { throw IllegalArgumentException("later") },
        )

        val thrown = assertThrows(RuntimeException::class.java) { all.close() }

        // Reverse order means "later" is closed first, so it is the one that propagates.
        assertEquals("later", thrown.message)
        assertEquals(listOf("earlier"), thrown.suppressed.map { it.message })
    }

    @Test
    fun `NONE closes without doing anything`() {
        SculkHandle.NONE.close()
        SculkHandle.NONE.close()
    }

    @Test
    fun `all over an empty list is a no-op`() {
        SculkHandle.all(emptyList()).close()
    }
}
