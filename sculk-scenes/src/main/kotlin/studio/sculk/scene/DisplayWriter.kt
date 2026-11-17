package studio.sculk.scene

import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack

/**
 * What a node's entity shows besides its options: rendered text, a built item stack or a parsed
 * block. Built by the caller, which holds the renderer and the server.
 */
internal sealed interface Content {
    class Text(val component: Component) : Content

    class Item(val stack: ItemStack) : Content

    class Block(val data: BlockData) : Content
}

/**
 * Writes nodes onto display entities. Must run on the thread that owns [Display]: inside the spawn
 * callback, before the entity joins the world, or on its region afterwards.
 */
internal object DisplayWriter {
    /** Everything a new entity needs, in the spawn callback so no viewer ever sees it half-drawn. */
    fun configure(display: Display, node: SceneNode, content: Content?, teleportTicks: Int, visibleByDefault: Boolean) {
        // Never saved: a crash or a missed close cannot leave a scene's entities in the world file.
        display.isPersistent = false
        display.isVisibleByDefault = visibleByDefault
        display.teleportDuration = teleportTicks
        write(display, node, Part.entries.toSet(), content)
    }

    /**
     * Writes [parts] of [node]. A transform carries its timing with it: the delay is rewritten on
     * every transform because the client starts interpolating only when that field arrives.
     */
    fun write(display: Display, node: SceneNode, parts: Set<Part>, content: Content?) {
        val options = node.display
        if (Part.LOOK in parts) {
            display.billboard = options.billboard
            display.brightness = options.brightness
            display.viewRange = options.viewRange
        }
        if (Part.TRANSFORM in parts || Part.TIMING in parts) {
            display.interpolationDuration = options.interpolationTicks
            display.interpolationDelay = options.interpolationDelayTicks
        }
        if (Part.TRANSFORM in parts) display.transformation = options.transform.toBukkit()
        when (node) {
            is TextNode -> if (display is TextDisplay) writeText(display, node.style, parts, content)
            is ItemNode -> if (display is ItemDisplay && Part.ITEM in parts) writeItem(display, node, content)
            is BlockNode -> if (display is BlockDisplay && Part.BLOCK in parts && content is Content.Block) display.block = content.data
        }
    }

    private fun writeText(display: TextDisplay, style: TextStyle, parts: Set<Part>, content: Content?) {
        if (Part.TEXT in parts && content is Content.Text) display.text(content.component)
        if (Part.STYLE !in parts) return
        display.isDefaultBackground = false
        display.backgroundColor = style.background
        display.isShadowed = style.shadow
        display.isSeeThrough = style.seeThrough
        display.alignment = style.alignment
        display.lineWidth = style.lineWidth
    }

    private fun writeItem(display: ItemDisplay, node: ItemNode, content: Content?) {
        if (content is Content.Item) display.setItemStack(content.stack)
        display.itemDisplayTransform = node.itemTransform
    }
}
