package studio.sculk.scene

import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import studio.sculk.annotation.SculkExperimental
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A position, offset or scale in a display's own space, in blocks. Every component is finite. */
@SculkExperimental
public data class Vec3(public val x: Float, public val y: Float, public val z: Float) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "a vector is finite, not ($x, $y, $z)" }
    }

    /** This vector with [other] added to it. */
    public fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    /** This vector with every component multiplied by [factor]. */
    public fun times(factor: Float): Vec3 = Vec3(x * factor, y * factor, z * factor)

    @SculkExperimental
    public companion object {
        public val ZERO: Vec3 = Vec3(0f, 0f, 0f)
        public val ONE: Vec3 = Vec3(1f, 1f, 1f)

        /** The same [value] on every axis, as for a uniform scale. */
        public fun of(value: Float): Vec3 = Vec3(value, value, value)
    }
}

/**
 * A rotation, kept as a unit quaternion with a non-negative w so that equal rotations compare equal.
 * Build one from [x], [y], [z] or [around] and combine them with [then].
 */
@SculkExperimental
public class Rotation private constructor(public val x: Float, public val y: Float, public val z: Float, public val w: Float) {
    /** This rotation followed by [next]. */
    public fun then(next: Rotation): Rotation = multiply(next, this)

    internal fun toJoml(): Quaternionf = Quaternionf(x, y, z, w)

    override fun equals(other: Any?): Boolean = other is Rotation && other.x == x && other.y == y && other.z == z && other.w == w

    override fun hashCode(): Int = ((x.hashCode() * 31 + y.hashCode()) * 31 + z.hashCode()) * 31 + w.hashCode()

    override fun toString(): String = "Rotation($x, $y, $z, $w)"

    @SculkExperimental
    public companion object {
        public val IDENTITY: Rotation = Rotation(0f, 0f, 0f, 1f)

        /** The rotation of quaternion ([x], [y], [z], [w]), normalised. Throws for a zero or non-finite quaternion. */
        public fun quaternion(x: Float, y: Float, z: Float, w: Float): Rotation {
            require(x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()) { "a quaternion is finite, not ($x, $y, $z, $w)" }
            val length = sqrt(x * x + y * y + z * z + w * w)
            require(length > 0f) { "a rotation cannot be the zero quaternion" }
            val sign = if (w < 0f) -1f else 1f
            val scale = sign / length
            // Adding zero turns -0.0 into 0.0, which equals() treats alike but hashCode() would not.
            return Rotation(x * scale + 0f, y * scale + 0f, z * scale + 0f, w * scale + 0f)
        }

        /** [degrees] counter-clockwise around [axis], looking from its tip. Throws for a zero axis. */
        public fun around(axis: Vec3, degrees: Float): Rotation {
            require(degrees.isFinite()) { "an angle is finite, not $degrees" }
            val length = sqrt(axis.x * axis.x + axis.y * axis.y + axis.z * axis.z)
            require(length > 0f) { "a rotation axis cannot be zero" }
            val half = Math.toRadians(degrees.toDouble()).toFloat() / 2f
            val s = sin(half) / length
            return quaternion(axis.x * s, axis.y * s, axis.z * s, cos(half))
        }

        /** [degrees] around the X axis: tilting forward or back. */
        public fun x(degrees: Float): Rotation = around(Vec3(1f, 0f, 0f), degrees)

        /** [degrees] around the Y axis: turning left or right. */
        public fun y(degrees: Float): Rotation = around(Vec3(0f, 1f, 0f), degrees)

        /** [degrees] around the Z axis: rolling sideways. */
        public fun z(degrees: Float): Rotation = around(Vec3(0f, 0f, 1f), degrees)

        // The Hamilton product a * b: applying b first, then a.
        private fun multiply(a: Rotation, b: Rotation): Rotation = quaternion(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
        )
    }
}

/**
 * Where a display draws relative to its entity, applied as Minecraft does: [scale] first, then
 * [rotation], then [translation]. [rightRotation] turns the model before it is scaled, for shear.
 */
@SculkExperimental
public data class Transform(
    public val translation: Vec3 = Vec3.ZERO,
    public val scale: Vec3 = Vec3.ONE,
    public val rotation: Rotation = Rotation.IDENTITY,
    public val rightRotation: Rotation = Rotation.IDENTITY,
) {
    /** This transform moved by [offset]. */
    public fun moved(offset: Vec3): Transform = copy(translation = translation.plus(offset))

    /** This transform scaled by [factor] on every axis. */
    public fun scaled(factor: Float): Transform = copy(scale = scale.times(factor))

    /** This transform with [by] applied after its rotation. */
    public fun rotated(by: Rotation): Transform = copy(rotation = rotation.then(by))

    internal fun toBukkit(): Transformation = Transformation(
        Vector3f(translation.x, translation.y, translation.z),
        rotation.toJoml(),
        Vector3f(scale.x, scale.y, scale.z),
        rightRotation.toJoml(),
    )

    @SculkExperimental
    public companion object {
        public val IDENTITY: Transform = Transform()
    }
}
