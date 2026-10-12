package studio.sculk.permission

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PermissionResolverTest {
    private val fly = Permission("travel.fly", default = PermissionDefault.NOBODY)
    private val warp = Permission("travel.warp", default = PermissionDefault.EVERYONE)
    private val vip = Permission(
        "rank.vip",
        default = PermissionDefault.NOBODY,
        children = mapOf(
            "travel.fly" to true,
            "travel.warp" to false,
        ),
    )
    private val staff = Permission("rank.staff", default = PermissionDefault.OPERATORS, children = mapOf("travel.fly" to true))
    private val resolver = PermissionResolver(listOf(fly, warp, vip, staff))

    private fun holds(permission: Permission, operator: Boolean = false, vararg set: Pair<String, Boolean>): Boolean =
        resolver.resolve(permission, operator) { node -> set.toMap()[node] }

    @Test
    fun `a value the subject sets beats every default`() {
        assertTrue(holds(fly, operator = false, "travel.fly" to true))
        assertFalse(holds(warp, operator = false, "travel.warp" to false))
    }

    @Test
    fun `a granted parent passes its children's values down`() {
        assertTrue(holds(fly, operator = false, "rank.vip" to true))
        assertFalse(holds(warp, operator = false, "rank.vip" to true), "vip sets warp to false")
    }

    @Test
    fun `a denied parent passes the opposite values down, as on Bukkit`() {
        assertFalse(holds(fly, operator = false, "rank.vip" to false))
        assertTrue(holds(warp, operator = false, "rank.vip" to false))
    }

    @Test
    fun `a value set on the node itself beats one passed down by a parent`() {
        assertFalse(holds(fly, operator = false, "rank.vip" to true, "travel.fly" to false))
    }

    @Test
    fun `defaults decide when nothing is set, including grants of a parent held by default`() {
        assertTrue(holds(warp))
        assertFalse(holds(fly))
        assertTrue(holds(fly, operator = true), "operators hold staff by default, and staff grants fly")
    }

    @Test
    fun `a descriptor that is not registered still has its own default`() {
        val unregistered = Permission("travel.home", default = PermissionDefault.NON_OPERATORS)
        assertTrue(holds(unregistered))
        assertFalse(holds(unregistered, operator = true))
        assertEquals(null, resolver.find("travel.home"))
        assertEquals(fly, resolver.find("travel.fly"))
    }

    @Test
    fun `children that form a cycle are cut rather than followed forever`() {
        val a = Permission("cycle.a", default = PermissionDefault.NOBODY, children = mapOf("cycle.b" to true))
        val b = Permission("cycle.b", default = PermissionDefault.NOBODY, children = mapOf("cycle.a" to true))
        val cyclic = PermissionResolver(listOf(a, b))
        assertFalse(cyclic.resolve(a, operator = true) { null })
        assertTrue(cyclic.resolve(a, operator = false) { node -> if (node == "cycle.b") true else null })
    }

    @Test
    fun `a node registered twice is refused`() {
        val failure = assertThrows<IllegalStateException> { PermissionResolver(listOf(fly, Permission("travel.fly"))) }
        assertEquals("travel.fly is registered twice.", failure.message)
    }
}
