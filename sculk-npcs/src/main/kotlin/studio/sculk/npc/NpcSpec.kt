package studio.sculk.npc

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.MainHand
import studio.sculk.annotation.SculkExperimental
import studio.sculk.item.ItemSpec
import studio.sculk.text.Text
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A trusted MiniMessage [template] and the [values] it reads as `<key>`. Values are inserted as
 * plain text and never parsed, so a player name with `<red>` in it is printed, not obeyed.
 */
@SculkExperimental
public class NpcText(public val template: String, values: Map<String, String> = emptyMap()) {
    public val values: Map<String, String> = values.toMap()

    init {
        require(template.isNotBlank()) { "an NPC's text template must not be blank" }
        this.values.keys.forEach { key ->
            require(TAG_NAME.matches(key)) { "a value's key is a tag name of a-z, 0-9, '_' and '-', not '$key'" }
        }
    }

    internal fun render(text: Text): Component = text.render(template, values)

    override fun equals(other: Any?): Boolean = other is NpcText && other.template == template && other.values == values

    override fun hashCode(): Int = 31 * template.hashCode() + values.hashCode()

    override fun toString(): String = "NpcText($template, $values)"

    private companion object {
        val TAG_NAME = Regex("[a-z0-9_-]+")
    }
}

/** The poses a mannequin can hold. Kept while the NPC lives; the server does not change it. */
@SculkExperimental
public enum class NpcPose {
    STANDING,
    SNEAKING,
    SWIMMING,
    GLIDING,
    SLEEPING,
}

/** Where an NPC carries an item. */
@SculkExperimental
public enum class NpcSlot {
    MAIN_HAND,
    OFF_HAND,
    HEAD,
    CHEST,
    LEGS,
    FEET,
}

/**
 * Turn the NPC's head towards the nearest player within [range] blocks every [everyTicks] ticks.
 * Only players the NPC's own region thread owns count, so on Folia and Canvas the check never
 * reaches into another region. With nobody in range the NPC turns back to where it was spawned facing.
 */
@SculkExperimental
public class LookAt(public val range: Double = 8.0, public val everyTicks: Long = 2) {
    init {
        require(range in 1.0..MAX_RANGE) { "look-at range must be 1 to $MAX_RANGE blocks, was $range" }
        require(everyTicks in 1..MAX_TICKS) { "look-at must run every 1 to $MAX_TICKS ticks, was $everyTicks" }
    }

    override fun equals(other: Any?): Boolean = other is LookAt && other.range == range && other.everyTicks == everyTicks

    override fun hashCode(): Int = 31 * range.hashCode() + everyTicks.hashCode()

    override fun toString(): String = "LookAt($range blocks, every $everyTicks ticks)"

    @SculkExperimental
    public companion object {
        /** Beyond this a player is usually outside the NPC's region or not tracking it at all. */
        public const val MAX_RANGE: Double = 32.0

        public const val MAX_TICKS: Long = 20
    }
}

/** A click on an NPC, handed to the spec's handler on the clicking player's own thread. */
@SculkExperimental
public class NpcClick internal constructor(
    public val player: Player,
    public val npc: Npc,
    public val button: Button,
    /** Whether the player was sneaking when they clicked. */
    public val sneaking: Boolean,
) {
    /** Which mouse button: [LEFT] attacks, [RIGHT] interacts. */
    @SculkExperimental
    public enum class Button { LEFT, RIGHT }

    override fun toString(): String = "NpcClick(${player.name}, $button, sneaking=$sneaking)"
}

/**
 * What an NPC looks like and does, independent of where it stands. Immutable and reusable: spawn
 * one spec as many times as you like, or derive variants with [with].
 *
 * ```kotlin
 * val shopkeeper = npc {
 *     name("<primary>Shopkeeper <name>", "name" to "Ada")
 *     description("<muted>Right-click to trade")
 *     skin = Skin.textures(value, signature)
 *     equip(NpcSlot.MAIN_HAND, item(Material.EMERALD))
 *     lookAtPlayers()
 *     onRightClick { click -> menus.open(click.player, shop, ShopState()) }
 * }
 * ```
 */
@SculkExperimental
public class NpcSpec internal constructor(builder: NpcBuilder) {
    /** The name above the NPC's head, the same for every viewer; null shows none. */
    public val name: NpcText? = builder.name

    /** The line under the name; null keeps Minecraft's own label, shown as "NPC". */
    public val description: NpcText? = builder.description

    /** Whether the line under the name shows at all. */
    public val showDescription: Boolean = builder.showDescription

    public val skin: Skin = builder.skin
    public val pose: NpcPose = builder.pose
    public val mainHand: MainHand = builder.mainHand
    public val equipment: Map<NpcSlot, ItemSpec> = builder.equipment.toMap()

    /** Head tracking, or null for an NPC that keeps facing where it was spawned. */
    public val lookAt: LookAt? = builder.lookAt

    /** Whether players and mobs bump into the NPC. */
    public val collidable: Boolean = builder.collidable

    /** Whether nothing can damage or kill the NPC. */
    public val invulnerable: Boolean = builder.invulnerable

    /** Whether the NPC makes no sounds, hurt sounds included. */
    public val silent: Boolean = builder.silent

    /** Whether nothing can push or knock the NPC back. */
    public val immovable: Boolean = builder.immovable

    /** A player's clicks with one button on one NPC closer together than this count once; zero counts every click. */
    public val clickCooldown: Duration = builder.clickCooldown

    public val onLeftClick: ((NpcClick) -> Unit)? = builder.onLeftClick
    public val onRightClick: ((NpcClick) -> Unit)? = builder.onRightClick

    init {
        require(!clickCooldown.isNegative() && clickCooldown <= MAX_COOLDOWN) {
            "clickCooldown must be 0 to $MAX_COOLDOWN, was $clickCooldown"
        }
    }

    /** A copy with [change] applied on top of this spec. */
    public fun with(change: NpcBuilder.() -> Unit): NpcSpec = NpcBuilder.from(this).apply(change).build()

    override fun toString(): String = "NpcSpec(name=${name?.template}, skin=$skin, pose=$pose)"

    @SculkExperimental
    public companion object {
        public val MAX_COOLDOWN: Duration = 1.minutes
    }
}

/** Marks the NPC DSL so a nested block cannot reach an outer builder by accident. */
@SculkExperimental
@DslMarker
public annotation class NpcDsl

/** Builds an [NpcSpec]; see [npc]. */
@SculkExperimental
@NpcDsl
public class NpcBuilder internal constructor() {
    internal var name: NpcText? = null
    internal var description: NpcText? = null
    internal var showDescription: Boolean = true
    internal val equipment = LinkedHashMap<NpcSlot, ItemSpec>()
    internal var lookAt: LookAt? = null
    internal var onLeftClick: ((NpcClick) -> Unit)? = null
    internal var onRightClick: ((NpcClick) -> Unit)? = null

    public var skin: Skin = Skin.Default
    public var pose: NpcPose = NpcPose.STANDING
    public var mainHand: MainHand = MainHand.RIGHT
    public var collidable: Boolean = false
    public var invulnerable: Boolean = true
    public var silent: Boolean = true
    public var immovable: Boolean = true
    public var clickCooldown: Duration = 200.milliseconds

    /** Shows [template] above the NPC's head, with [values] as plain-text placeholders. */
    public fun name(template: String, vararg values: Pair<String, String>) {
        name = NpcText(template, values.toMap())
    }

    /** Shows [template] under the name in place of Minecraft's "NPC" label. */
    public fun description(template: String, vararg values: Pair<String, String>) {
        description = NpcText(template, values.toMap())
        showDescription = true
    }

    /** Shows no line under the name. */
    public fun hideDescription() {
        description = null
        showDescription = false
    }

    /** Puts [item] in [slot]; the NPC never drops it and players cannot take it. */
    public fun equip(slot: NpcSlot, item: ItemSpec) {
        equipment[slot] = item
    }

    /** Turns the NPC's head towards the nearest player; see [LookAt]. */
    public fun lookAtPlayers(range: Double = 8.0, everyTicks: Long = 2) {
        lookAt = LookAt(range, everyTicks)
    }

    /** Runs [handler] on the player's thread when a player hits the NPC. The hit does no damage. */
    public fun onLeftClick(handler: (NpcClick) -> Unit) {
        onLeftClick = handler
    }

    /** Runs [handler] on the player's thread when a player right-clicks the NPC with their main hand. */
    public fun onRightClick(handler: (NpcClick) -> Unit) {
        onRightClick = handler
    }

    /** Runs [handler] for either button; [NpcClick.button] says which. */
    public fun onClick(handler: (NpcClick) -> Unit) {
        onLeftClick = handler
        onRightClick = handler
    }

    internal fun build(): NpcSpec = NpcSpec(this)

    internal companion object {
        fun from(spec: NpcSpec): NpcBuilder = NpcBuilder().apply {
            name = spec.name
            description = spec.description
            showDescription = spec.showDescription
            equipment.putAll(spec.equipment)
            lookAt = spec.lookAt
            onLeftClick = spec.onLeftClick
            onRightClick = spec.onRightClick
            skin = spec.skin
            pose = spec.pose
            mainHand = spec.mainHand
            collidable = spec.collidable
            invulnerable = spec.invulnerable
            silent = spec.silent
            immovable = spec.immovable
            clickCooldown = spec.clickCooldown
        }
    }
}

/** Declares an NPC; see [NpcSpec]. */
@SculkExperimental
public fun npc(build: NpcBuilder.() -> Unit): NpcSpec = NpcBuilder().apply(build).build()
