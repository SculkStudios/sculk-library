@file:JvmName("SculkBlocks")

package studio.sculk.series

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.CalibratedSculkSensor
import org.bukkit.block.SculkCatalyst
import org.bukkit.block.SculkSensor
import org.bukkit.block.SculkShrieker
import studio.sculk.annotation.SculkExperimental

/**
 * The Minecraft Sculk block family (the in-game blocks, not the framework).
 *
 * Kotlin:
 * ```kotlin
 * if (block.type in SCULK_BLOCKS) { /* ... */ }
 * ```
 * Java:
 * ```java
 * if (SculkBlocks.isSculkBlock(block.getType())) { /* ... */ }
 * ```
 */
@SculkExperimental
public val SCULK_BLOCKS: Set<Material> =
    setOf(
        Material.SCULK,
        Material.SCULK_VEIN,
        Material.SCULK_SENSOR,
        Material.CALIBRATED_SCULK_SENSOR,
        Material.SCULK_CATALYST,
        Material.SCULK_SHRIEKER,
    )

/** Returns true if [material] is part of the Sculk block family. Java-callable as `SculkBlocks.isSculkBlock(...)`. */
@SculkExperimental
public fun isSculkBlock(material: Material): Boolean = material in SCULK_BLOCKS

/** Returns true if [block] is a Sculk block. */
@SculkExperimental
public fun isSculkBlock(block: Block): Boolean = isSculkBlock(block.type)

/** Returns the [SculkSensor] state at [location], or null if the block there is not a Sculk sensor. */
@SculkExperimental
public fun sculkSensorAt(location: Location): SculkSensor? = location.block.state as? SculkSensor

/** Returns the [CalibratedSculkSensor] state at [location], or null if not a calibrated Sculk sensor. */
@SculkExperimental
public fun calibratedSculkSensorAt(location: Location): CalibratedSculkSensor? = location.block.state as? CalibratedSculkSensor

/** Returns the [SculkCatalyst] state at [location], or null if the block there is not a Sculk catalyst. */
@SculkExperimental
public fun sculkCatalystAt(location: Location): SculkCatalyst? = location.block.state as? SculkCatalyst

/** Returns the [SculkShrieker] state at [location], or null if the block there is not a Sculk shrieker. */
@SculkExperimental
public fun sculkShriekerAt(location: Location): SculkShrieker? = location.block.state as? SculkShrieker
