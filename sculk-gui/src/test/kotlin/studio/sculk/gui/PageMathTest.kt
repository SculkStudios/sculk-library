package studio.sculk.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageMathTest {
    @Test
    fun `an empty list still occupies one page`() {
        // A menu that reports zero pages renders "page 1 of 0" and divides by zero when paging.
        assertEquals(1, pageCount(entryCount = 0, perPage = 9))
    }

    @Test
    fun `a partial final page still counts`() {
        assertEquals(1, pageCount(9, 9))
        assertEquals(2, pageCount(10, 9))
        assertEquals(2, pageCount(18, 9))
        assertEquals(3, pageCount(19, 9))
    }

    @Test
    fun `a single entry fits on one page`() {
        assertEquals(1, pageCount(1, 9))
    }

    @Test
    fun `a zero page size does not divide by zero`() {
        assertEquals(1, pageCount(20, 0))
        assertEquals(IntRange.EMPTY, pageRange(0, 20, 0))
    }

    @Test
    fun `clamping keeps a page inside the range`() {
        assertEquals(0, clampPage(-5, entryCount = 20, perPage = 9))
        assertEquals(2, clampPage(99, entryCount = 20, perPage = 9))
        assertEquals(1, clampPage(1, entryCount = 20, perPage = 9))
    }

    @Test
    fun `clamping an empty list lands on page zero`() {
        assertEquals(0, clampPage(3, entryCount = 0, perPage = 9))
    }

    @Test
    fun `a page range covers exactly its slice`() {
        assertEquals(0 until 9, pageRange(0, 20, 9))
        assertEquals(9 until 18, pageRange(1, 20, 9))
        assertEquals(18 until 20, pageRange(2, 20, 9), "the last page is short, not padded")
    }

    @Test
    fun `a page past the end is empty rather than negative`() {
        assertTrue(pageRange(5, 20, 9).isEmpty())
    }
}
