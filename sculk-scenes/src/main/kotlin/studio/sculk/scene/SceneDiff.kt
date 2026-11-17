package studio.sculk.scene

/** A group of display properties that change together, so a redraw writes only those. */
internal enum class Part {
    /** The text template, re-rendered. */
    TEXT,

    /** Background, shadow, see-through, alignment and line width. */
    STYLE,
    ITEM,
    BLOCK,

    /** The transformation, slid over the node's interpolation. */
    TRANSFORM,

    /** Billboard, brightness and view range. */
    LOOK,

    /** Interpolation duration and delay on their own, applied with the next transform. */
    TIMING,
}

/** How one node differs between two specs. */
internal sealed interface NodeChange {
    val id: String

    data class Added(val node: SceneNode) : NodeChange {
        override val id: String get() = node.id
    }

    data class Removed(override val id: String) : NodeChange

    /** The kind of display changed, or text became per-viewer or shared: the entity is replaced. */
    data class Replaced(val node: SceneNode) : NodeChange {
        override val id: String get() = node.id
    }

    data class Changed(val node: SceneNode, val parts: Set<Part>) : NodeChange {
        override val id: String get() = node.id
    }
}

/** Compares specs node by node, by id. Pure: no server, no entities. */
internal object SceneDiff {
    /** What changed from [old] to [new]; unchanged nodes are left out. Removals come first, then [new]'s order. */
    fun between(old: SceneSpec, new: SceneSpec): List<NodeChange> {
        val before = old.nodes.associateBy { it.id }
        val after = new.nodes.associateBy { it.id }
        val removed = old.nodes.filter { it.id !in after }.map { NodeChange.Removed(it.id) }
        val rest = new.nodes.mapNotNull { node ->
            val previous = before[node.id] ?: return@mapNotNull NodeChange.Added(node)
            if (previous == node) return@mapNotNull null
            val parts = parts(previous, node) ?: return@mapNotNull NodeChange.Replaced(node)
            NodeChange.Changed(node, parts)
        }
        return removed + rest
    }

    /** The parts that differ between two nodes with one id, or null when the entity must be replaced. */
    fun parts(old: SceneNode, new: SceneNode): Set<Part>? {
        val parts = mutableSetOf<Part>()
        when {
            old is TextNode && new is TextNode -> {
                if (old.perViewer != new.perViewer) return null
                if (old.text != new.text) parts += Part.TEXT
                if (old.style != new.style) parts += Part.STYLE
            }

            old is ItemNode && new is ItemNode -> {
                if (old.item != new.item || old.itemTransform != new.itemTransform) parts += Part.ITEM
            }

            old is BlockNode && new is BlockNode -> {
                if (old.state != new.state) parts += Part.BLOCK
            }

            else -> return null
        }
        val before = old.display
        val after = new.display
        if (before.transform != after.transform) parts += Part.TRANSFORM
        if (before.billboard != after.billboard || before.brightness != after.brightness || before.viewRange != after.viewRange) {
            parts += Part.LOOK
        }
        if (before.interpolationTicks != after.interpolationTicks || before.interpolationDelayTicks != after.interpolationDelayTicks) {
            parts += Part.TIMING
        }
        return parts
    }
}
