package studio.sculk.core.gui

import org.bukkit.Material
import studio.sculk.core.annotation.SculkStable

/**
 * Common chest-inventory slot calculations for stable menu layouts.
 */
@SculkStable
public object GuiSlots {
    /** Returns all slots in [row], where row 0 is the top row. */
    public fun row(
        row: Int,
        size: Int,
    ): List<Int> {
        require(size % 9 == 0 && size in 9..54) { "GUI size must be a multiple of 9 between 9 and 54." }
        val rows = size / 9
        require(row in 0 until rows) { "Row $row is out of range for a GUI with $rows rows." }
        val start = row * 9
        return (start until start + 9).toList()
    }

    /** Returns the top and bottom rows only. Useful for subtle menu borders. */
    public fun horizontalBorder(size: Int): List<Int> {
        require(size % 9 == 0 && size in 9..54) { "GUI size must be a multiple of 9 between 9 and 54." }
        return (row(0, size) + row((size / 9) - 1, size)).distinct()
    }

    /** Returns the full outer ring: top, bottom, left, and right columns. */
    public fun outerRing(size: Int): List<Int> {
        require(size % 9 == 0 && size in 9..54) { "GUI size must be a multiple of 9 between 9 and 54." }
        val rows = size / 9
        return (0 until size)
            .filter { slot ->
                val row = slot / 9
                val column = slot % 9
                row == 0 || row == rows - 1 || column == 0 || column == 8
            }
    }
}

@SculkStable
public enum class GuiBorderStyle {
    Horizontal,
    OuterRing,
}

/**
 * Fills a common border shape while skipping reserved control slots.
 */
@SculkStable
public fun GuiBuilder.border(
    material: Material,
    style: GuiBorderStyle,
    skip: Set<Int> = emptySet(),
    block: GuiItemBuilder.() -> Unit = {},
) {
    val slots =
        when (style) {
            GuiBorderStyle.Horizontal -> GuiSlots.horizontalBorder(size)
            GuiBorderStyle.OuterRing -> GuiSlots.outerRing(size)
        }
    items(slots.filterNot(skip::contains)) {
        this.material = material
        block()
    }
}
