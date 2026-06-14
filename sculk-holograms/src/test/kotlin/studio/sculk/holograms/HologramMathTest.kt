package studio.sculk.holograms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HologramMathTest {
    @Test
    fun `chunk radius covers the view range`() {
        assertEquals(1, HologramMath.chunkRadius(0.0))
        assertEquals(1, HologramMath.chunkRadius(16.0))
        assertEquals(3, HologramMath.chunkRadius(48.0))
        assertEquals(4, HologramMath.chunkRadius(50.0))
    }

    @Test
    fun `viewer diff computes adds and removes`() {
        val current = setOf("a", "b")
        val desired = setOf("b", "c")
        assertEquals(listOf("c"), HologramMath.toAdd(current, desired))
        assertEquals(listOf("a"), HologramMath.toRemove(current, desired))
    }

    @Test
    fun `entity id allocator hands out unique ids`() {
        val allocator = EntityIdAllocator(start = 1000)
        val ids = (0 until 500).map { allocator.next() }
        assertEquals(500, ids.toSet().size)
        assertEquals(1000, ids.first())
        assertTrue(ids.all { it <= 1000 })
    }
}
