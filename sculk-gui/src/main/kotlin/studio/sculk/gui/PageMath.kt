package studio.sculk.gui

/**
 * How many pages [entryCount] entries fill at [perPage] per page.
 *
 * Always at least one, so an empty list still opens on a page rather than on a menu that reports
 * "page 1 of 0" or divides by zero. Hoisted out of [GuiSession] and made internal so the rounding
 * can be tested without a server — it was written out three separate times inline, and the three
 * did not agree about the empty case.
 */
internal fun pageCount(entryCount: Int, perPage: Int): Int {
    if (perPage <= 0) return 1
    if (entryCount <= 0) return 1
    return (entryCount + perPage - 1) / perPage
}

/** Clamps [page] into the range [pageCount] allows. */
internal fun clampPage(page: Int, entryCount: Int, perPage: Int): Int = page.coerceIn(0, pageCount(entryCount, perPage) - 1)

/** The slice of entry indices shown on [page]. Empty when the page is past the end. */
internal fun pageRange(page: Int, entryCount: Int, perPage: Int): IntRange {
    if (perPage <= 0 || entryCount <= 0) return IntRange.EMPTY
    val start = page * perPage
    if (start >= entryCount) return IntRange.EMPTY
    return start until minOf(start + perPage, entryCount)
}
