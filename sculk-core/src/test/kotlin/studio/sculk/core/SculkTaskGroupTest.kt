package studio.sculk.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SculkTaskGroupTest {
    @Test
    fun `group closes handles in reverse registration order`() {
        val closed = mutableListOf<Int>()
        val group =
            taskGroup {
                add(SculkHandle { closed += 1 })
                add(SculkHandle { closed += 2 })
                add(SculkHandle { closed += 3 })
            }

        group.close()

        assertEquals(listOf(3, 2, 1), closed)
    }

    @Test
    fun `group close is idempotent`() {
        var closes = 0
        val group =
            taskGroup {
                add(SculkHandle { closes++ })
            }

        group.close()
        group.close()

        assertEquals(1, closes)
    }

    @Test
    fun `handle added after close is closed immediately`() {
        var closes = 0
        val group = SculkTaskGroup()

        group.close()
        group.add(SculkHandle { closes++ })

        assertEquals(1, closes)
    }
}
