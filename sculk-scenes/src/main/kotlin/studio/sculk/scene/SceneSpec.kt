package studio.sculk.scene

import org.bukkit.Color
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import studio.sculk.annotation.SculkExperimental
import studio.sculk.item.ItemSpec

/**
 * How any display draws, whatever it shows. [interpolationTicks] is how long the client takes to
 * slide to a new [transform], starting [interpolationDelayTicks] after it arrives; zero snaps.
 * [viewRange] scales the distance the display is drawn from, 1 being Minecraft's default.
 */
@SculkExperimental
public data class DisplayOptions(
    public val transform: Transform = Transform.IDENTITY,
    public val billboard: Display.Billboard = Display.Billboard.FIXED,
    /** Block and sky light, each 0 to 15; null lights the display from the world around it. */
    public val brightness: Display.Brightness? = null,
    public val viewRange: Float = 1f,
    public val interpolationTicks: Int = 0,
    public val interpolationDelayTicks: Int = 0,
) {
    init {
        require(viewRange.isFinite() && viewRange >= 0f) { "a view range is zero or more, not $viewRange" }
        require(interpolationTicks >= 0) { "interpolation lasts zero ticks or more, not $interpolationTicks" }
        require(interpolationDelayTicks >= 0) { "an interpolation delay is zero ticks or more, not $interpolationDelayTicks" }
    }
}

/**
 * How a text display draws its text. A null [background] is Minecraft's translucent default; a
 * colour with zero alpha removes it. [lineWidth] is in pixels, after which the client wraps.
 */
@SculkExperimental
public data class TextStyle(
    public val background: Color? = null,
    public val shadow: Boolean = false,
    public val seeThrough: Boolean = false,
    public val alignment: TextDisplay.TextAlignment = TextDisplay.TextAlignment.CENTER,
    public val lineWidth: Int = DEFAULT_LINE_WIDTH,
) {
    init {
        require(lineWidth > 0) { "a line is at least one pixel wide, not $lineWidth" }
    }

    @SculkExperimental
    public companion object {
        /** Minecraft's default wrap width. */
        public const val DEFAULT_LINE_WIDTH: Int = 200
    }
}

/**
 * One display in a scene, told apart from the others by [id]: lower-case letters, digits, `_`, `-`
 * and `.`, at most 64 characters. A node with the same id in a later spec is the same display.
 */
@SculkExperimental
public sealed class SceneNode {
    public abstract val id: String
    public abstract val display: DisplayOptions

    internal abstract fun withDisplay(display: DisplayOptions): SceneNode

    internal companion object {
        private val ID = Regex("[a-z0-9_.-]{1,64}")

        fun requireId(id: String) {
            require(ID.matches(id)) { "a node id is 1 to 64 of a-z, 0-9, '_', '-' and '.', not '$id'" }
        }
    }
}

/**
 * A text display. [text] is a trusted MiniMessage template; values reach it only as placeholders.
 * Shared text is rendered once for every viewer; with [perViewer] each viewer gets their own
 * rendering, which needs the scene to be shown only to chosen players (ADR-0032).
 */
@SculkExperimental
public data class TextNode(
    override val id: String,
    public val text: String,
    public val style: TextStyle = TextStyle(),
    override val display: DisplayOptions = DisplayOptions(billboard = Display.Billboard.CENTER),
    public val perViewer: Boolean = false,
) : SceneNode() {
    init {
        SceneNode.requireId(id)
    }

    override fun withDisplay(display: DisplayOptions): SceneNode = copy(display = display)
}

/** An item display showing [item], posed as [itemTransform] says an item is held or placed. */
@SculkExperimental
public data class ItemNode(
    override val id: String,
    public val item: ItemSpec,
    public val itemTransform: ItemDisplay.ItemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE,
    override val display: DisplayOptions = DisplayOptions(),
) : SceneNode() {
    init {
        SceneNode.requireId(id)
    }

    override fun withDisplay(display: DisplayOptions): SceneNode = copy(display = display)
}

/**
 * A block display. The block is kept as its [state] string, such as `minecraft:oak_stairs[facing=east]`,
 * so the node stays immutable however the [BlockData] it was made from changes afterwards.
 */
@SculkExperimental
public class BlockNode private constructor(override val id: String, public val state: String, override val display: DisplayOptions) :
    SceneNode() {
    public constructor(id: String, block: BlockData, display: DisplayOptions = DisplayOptions()) : this(id, block.asString, display)

    init {
        SceneNode.requireId(id)
    }

    /** This node with another [id] or [display]; the block stays. */
    public fun copy(id: String = this.id, display: DisplayOptions = this.display): BlockNode = BlockNode(id, state, display)

    /** This node showing [block] instead. */
    public fun withBlock(block: BlockData): BlockNode = BlockNode(id, block.asString, display)

    override fun withDisplay(display: DisplayOptions): SceneNode = copy(display = display)

    override fun equals(other: Any?): Boolean = other is BlockNode && other.id == id && other.state == state && other.display == display

    override fun hashCode(): Int = (id.hashCode() * 31 + state.hashCode()) * 31 + display.hashCode()

    override fun toString(): String = "BlockNode($id, $state, $display)"
}

/**
 * What a scene shows: its [nodes] in order, each a display at the scene's anchor offset by its own
 * transform. [teleportTicks], 0 to 59, is how long the client takes to glide each display to a new
 * position when the scene moves; zero jumps.
 *
 * ```kotlin
 * val board = scene {
 *     text("title", "<primary>Daily rewards") { transform = Transform(translation = Vec3(0f, 0.6f, 0f)) }
 *     text("streak", "<muted>Streak: <value><rewards_streak>", perViewer = true)
 *     item("icon", item(Material.CHEST)) { transform = Transform(scale = Vec3.of(0.5f)) }
 * }
 * ```
 */
@SculkExperimental
public class SceneSpec internal constructor(nodes: Collection<SceneNode>, public val teleportTicks: Int) {
    public val nodes: List<SceneNode> = nodes.toList()

    init {
        require(this.nodes.size <= MAX_NODES) { "a scene has at most $MAX_NODES nodes, not ${this.nodes.size}" }
        val ids = this.nodes.map { it.id }
        require(ids.distinct().size == ids.size) {
            "node ids in a scene must differ: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}"
        }
        require(teleportTicks in 0..MAX_TELEPORT_TICKS) { "teleportTicks is 0 to $MAX_TELEPORT_TICKS, not $teleportTicks" }
    }

    /** The node with [id], or null. */
    public fun node(id: String): SceneNode? = nodes.firstOrNull { it.id == id }

    /** A copy with [change] applied: nodes added under an existing id replace it in place. */
    public fun with(change: SceneBuilder.() -> Unit): SceneSpec = SceneBuilder(nodes, teleportTicks).apply(change).build()

    /** A copy whose text node [id] shows [text]. Throws when there is no text node [id]. */
    public fun withText(id: String, text: String): SceneSpec = change<TextNode>(id) { it.copy(text = text) }

    /** A copy whose node [id] is drawn with [transform]. Throws when there is no node [id]. */
    public fun withTransform(id: String, transform: Transform): SceneSpec {
        val current = requireNode(id)
        val changed = current.withDisplay(current.display.copy(transform = transform))
        return with { node(changed) }
    }

    /** A copy whose node [id], which must be a [T], is replaced by [change] of it under the same id. */
    public inline fun <reified T : SceneNode> change(id: String, change: (T) -> T): SceneSpec {
        val current = requireNode(id)
        require(current is T) { "node '$id' is a ${current::class.simpleName}, not a ${T::class.simpleName}" }
        val changed = change(current)
        require(changed.id == id) { "a changed node keeps its id '$id', not '${changed.id}'" }
        return with { node(changed) }
    }

    /** The node with [id]; throws [IllegalArgumentException] when there is none. */
    public fun requireNode(id: String): SceneNode = requireNotNull(node(id)) { "this scene has no node '$id'" }

    override fun equals(other: Any?): Boolean = other is SceneSpec && other.teleportTicks == teleportTicks && other.nodes == nodes

    override fun hashCode(): Int = nodes.hashCode() * 31 + teleportTicks

    override fun toString(): String = "SceneSpec(${nodes.joinToString { it.id }}, teleportTicks=$teleportTicks)"

    @SculkExperimental
    public companion object {
        public const val MAX_NODES: Int = 256

        /** The longest position interpolation Minecraft accepts. */
        public const val MAX_TELEPORT_TICKS: Int = 59

        /** A scene with nothing in it. */
        public val EMPTY: SceneSpec = SceneSpec(emptyList(), 0)
    }
}

/** Marks the scene DSL so a node block cannot reach the scene builder by accident. */
@SculkExperimental
@DslMarker
public annotation class SceneDsl

/** Builds a [SceneSpec]; see [scene]. Nodes keep the order they were first added in. */
@SculkExperimental
@SceneDsl
public class SceneBuilder internal constructor(nodes: List<SceneNode>, teleportTicks: Int) {
    private val nodes = LinkedHashMap<String, SceneNode>().apply { nodes.forEach { put(it.id, it) } }

    /** See [SceneSpec.teleportTicks]. */
    public var teleportTicks: Int = teleportTicks

    /** Adds [node], or replaces the node with its id where it stands. */
    public fun node(node: SceneNode) {
        nodes[node.id] = node
    }

    /** Removes the node [id], if there is one. */
    public fun remove(id: String) {
        nodes.remove(id)
    }

    /** Adds a text node; see [TextNode]. */
    public fun text(id: String, text: String, perViewer: Boolean = false, configure: TextNodeBuilder.() -> Unit = {}) {
        node(TextNodeBuilder().apply(configure).build(id, text, perViewer))
    }

    /** Adds an item node; see [ItemNode]. */
    public fun item(id: String, item: ItemSpec, configure: ItemNodeBuilder.() -> Unit = {}) {
        node(ItemNodeBuilder().apply(configure).build(id, item))
    }

    /** Adds a block node; see [BlockNode]. */
    public fun block(id: String, block: BlockData, configure: BlockNodeBuilder.() -> Unit = {}) {
        node(BlockNodeBuilder().apply(configure).build(id, block))
    }

    internal fun build(): SceneSpec = SceneSpec(nodes.values, teleportTicks)
}

/** What every node builder sets; see [DisplayOptions]. */
@SculkExperimental
@SceneDsl
public abstract class DisplayBuilder internal constructor(billboard: Display.Billboard) {
    public var transform: Transform = Transform.IDENTITY
    public var billboard: Display.Billboard = billboard
    public var brightness: Display.Brightness? = null
    public var viewRange: Float = 1f
    public var interpolationTicks: Int = 0
    public var interpolationDelayTicks: Int = 0

    internal fun options(): DisplayOptions =
        DisplayOptions(transform, billboard, brightness, viewRange, interpolationTicks, interpolationDelayTicks)
}

/** Builds a [TextNode]; billboarded to face the viewer unless told otherwise. */
@SculkExperimental
public class TextNodeBuilder internal constructor() : DisplayBuilder(Display.Billboard.CENTER) {
    public var background: Color? = null
    public var shadow: Boolean = false
    public var seeThrough: Boolean = false
    public var alignment: TextDisplay.TextAlignment = TextDisplay.TextAlignment.CENTER
    public var lineWidth: Int = TextStyle.DEFAULT_LINE_WIDTH

    internal fun build(id: String, text: String, perViewer: Boolean): TextNode =
        TextNode(id, text, TextStyle(background, shadow, seeThrough, alignment, lineWidth), options(), perViewer)
}

/** Builds an [ItemNode]. */
@SculkExperimental
public class ItemNodeBuilder internal constructor() : DisplayBuilder(Display.Billboard.FIXED) {
    public var itemTransform: ItemDisplay.ItemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE

    internal fun build(id: String, item: ItemSpec): ItemNode = ItemNode(id, item, itemTransform, options())
}

/** Builds a [BlockNode]. */
@SculkExperimental
public class BlockNodeBuilder internal constructor() : DisplayBuilder(Display.Billboard.FIXED) {
    internal fun build(id: String, block: BlockData): BlockNode = BlockNode(id, block, options())
}

/** Declares a [SceneSpec]. */
@SculkExperimental
public fun scene(build: SceneBuilder.() -> Unit): SceneSpec = SceneBuilder(emptyList(), 0).apply(build).build()
