package studio.sculk.scene

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.math.sqrt

/** Vectors, rotations and transforms: the maths a display's pose rests on. */
class GeometryTest {
    private fun assertNear(expected: Float, actual: Float, what: String) {
        assertTrue(abs(expected - actual) < 1e-5f) { "$what: expected $expected, was $actual" }
    }

    private fun assertSame(expected: Rotation, actual: Rotation) {
        assertNear(expected.x, actual.x, "x")
        assertNear(expected.y, actual.y, "y")
        assertNear(expected.z, actual.z, "z")
        assertNear(expected.w, actual.w, "w")
    }

    @Test
    fun `a vector refuses infinite and missing components`() {
        assertThrows<IllegalArgumentException> { Vec3(Float.NaN, 0f, 0f) }
        assertThrows<IllegalArgumentException> { Vec3(0f, Float.POSITIVE_INFINITY, 0f) }
        assertEquals(Vec3(1f, 2f, 3f), Vec3(0.5f, 1f, 1.5f).times(2f))
        assertEquals(Vec3(1f, 1f, 1f), Vec3.ZERO.plus(Vec3.ONE))
    }

    @Test
    fun `a rotation is kept normalised with a non-negative w, so equal rotations compare equal`() {
        val scaled = Rotation.quaternion(0f, 0f, 0f, 5f)
        assertEquals(Rotation.IDENTITY, scaled)
        assertEquals(Rotation.quaternion(0f, 1f, 0f, 1f), Rotation.quaternion(0f, -1f, 0f, -1f))
        val half = sqrt(0.5f)
        assertSame(Rotation.quaternion(0f, half, 0f, half), Rotation.y(90f))
    }

    @Test
    fun `a zero quaternion or a zero axis is refused`() {
        assertThrows<IllegalArgumentException> { Rotation.quaternion(0f, 0f, 0f, 0f) }
        assertThrows<IllegalArgumentException> { Rotation.around(Vec3.ZERO, 30f) }
        assertThrows<IllegalArgumentException> { Rotation.y(Float.NaN) }
    }

    @Test
    fun `rotations compose in the order they are applied`() {
        assertSame(Rotation.y(90f), Rotation.y(30f).then(Rotation.y(60f)))
        assertSame(Rotation.IDENTITY, Rotation.x(45f).then(Rotation.x(-45f)))
        val yThenX = Rotation.y(90f).then(Rotation.x(90f))
        val xThenY = Rotation.x(90f).then(Rotation.y(90f))
        assertNotEquals(xThenY, yThenX, "rotation order must matter")
        assertSame(Rotation.quaternion(0.5f, 0.5f, 0.5f, 0.5f), yThenX)
        assertSame(Rotation.quaternion(0.5f, 0.5f, -0.5f, 0.5f), xThenY)
    }

    @Test
    fun `an axis need not be a unit vector`() {
        assertSame(Rotation.z(90f), Rotation.around(Vec3(0f, 0f, 7f), 90f))
    }

    @Test
    fun `a transform turns into the Bukkit transformation Minecraft applies`() {
        val transform = Transform(translation = Vec3(0f, 1.5f, 0f), scale = Vec3.of(2f), rotation = Rotation.y(90f))
        val bukkit = transform.toBukkit()
        assertEquals(1.5f, bukkit.translation.y)
        assertEquals(2f, bukkit.scale.x)
        assertNear(Rotation.y(90f).y, bukkit.leftRotation.y, "left rotation y")
        assertEquals(1f, bukkit.rightRotation.w)
    }

    @Test
    fun `transform helpers change one part and keep the rest`() {
        val base = Transform(translation = Vec3(1f, 0f, 0f), scale = Vec3.of(0.5f))
        assertEquals(Vec3(1f, 2f, 0f), base.moved(Vec3(0f, 2f, 0f)).translation)
        assertEquals(Vec3.of(0.5f), base.moved(Vec3(0f, 2f, 0f)).scale)
        assertEquals(Vec3.of(1f), base.scaled(2f).scale)
        assertSame(Rotation.y(10f), base.rotated(Rotation.y(10f)).rotation)
        assertEquals(base.translation, base.rotated(Rotation.y(10f)).translation)
    }
}
