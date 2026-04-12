package gg.sculk.core.gui.java

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.gui.Gui
import gg.sculk.core.gui.GuiBuilder
import java.util.function.Consumer

/**
 * Fluent Java-compatible GUI builder for Sculk Studio.
 *
 * Kotlin users should use the [gg.sculk.core.gui.gui] DSL instead.
 *
 * ```java
 * Gui menu = JavaGui.builder("<dark_aqua><bold>Main Menu")
 *     .size(27)
 *     .item(13, item -> item
 *         .material(Material.NETHER_STAR)
 *         .name("<gold><bold>Settings")
 *         .lore("<gray>Click to open settings.")
 *         .onClick(ctx -> ctx.open(settingsMenu))
 *     )
 *     .build();
 * ```
 */
@SculkStable
public class JavaGuiBuilder internal constructor(
    private val builder: GuiBuilder,
) {
    /** Sets the number of inventory slots. Must be a multiple of 9 between 9 and 54. Defaults to 27. */
    public fun size(size: Int): JavaGuiBuilder {
        builder.size = size
        return this
    }

    /**
     * Defines an item at [slot], configured via [configurator].
     *
     * Slots are 0-indexed from top-left to bottom-right.
     */
    public fun item(
        slot: Int,
        configurator: Consumer<JavaGuiItemBuilder>,
    ): JavaGuiBuilder {
        builder.item(slot) {
            configurator.accept(JavaGuiItemBuilder(this))
        }
        return this
    }

    /**
     * Configures pagination with the given [slots].
     *
     * The list defines which slots hold paginated entries.
     * Wire up navigation with `onClick(ctx -> ctx.nextPage())` / `ctx.previousPage()`.
     *
     * ```java
     * .pagination(IntStream.range(0, 45).boxed().collect(Collectors.toList()))
     * ```
     */
    public fun pagination(slots: List<Int>): JavaGuiBuilder {
        builder.pagination {
            this.slots += slots
        }
        return this
    }

    /** Builds and returns the [Gui]. */
    @OptIn(SculkInternal::class)
    public fun build(): Gui = builder.build()
}

/**
 * Entry point for building GUIs from Java.
 *
 * Kotlin users should use the [gg.sculk.core.gui.gui] DSL instead.
 *
 * ```java
 * Gui menu = JavaGui.builder("<green>My Menu")
 *     .size(27)
 *     .item(13, item -> item.material(Material.DIAMOND).name("<aqua>Click me"))
 *     .build();
 * ```
 */
@SculkStable
public object JavaGui {
    /**
     * Creates a new [JavaGuiBuilder] for a GUI with the given MiniMessage [title].
     *
     * ```java
     * Gui menu = JavaGui.builder("<dark_aqua><bold>Shop").size(54).build();
     * ```
     */
    @JvmStatic
    @OptIn(SculkInternal::class)
    public fun builder(title: String): JavaGuiBuilder = JavaGuiBuilder(GuiBuilder(title))
}
