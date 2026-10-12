package studio.sculk.testkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionDefault

class FakePermissionsTest {
    private val use = Permission("kits.use", default = PermissionDefault.EVERYONE)
    private val all = Permission("kits.*", default = PermissionDefault.NOBODY, children = mapOf("kits.use" to true, "kits.give" to true))

    @Test
    fun `registered descriptors decide raw string checks as a server's registry would`() {
        val permissions = FakePermissions()
        val player = permissions.subject()
        assertFalse(player.hasPermission("kits.use"), "unregistered, a node is held by operators only")
        val registration = permissions.register(use, all)
        assertTrue(player.hasPermission("kits.use"))
        registration.close()
        assertFalse(player.hasPermission("kits.use"))
        assertEquals(emptyList<Permission>(), permissions.registered)
    }

    @Test
    fun `a parent set on the subject grants its registered children`() {
        val permissions = FakePermissions().apply { register(use, all) }
        val player = permissions.subject(permissions = mapOf("kits.*" to true))
        assertTrue(player.hasPermission(Permission("kits.give", default = PermissionDefault.NOBODY)))
        player.set("kits.*", false)
        assertFalse(player.hasPermission(use), "a denied parent inverts the child's value")
        player.unset("kits.*")
        assertTrue(player.hasPermission(use))
    }

    @Test
    fun `a node registered twice is refused and nothing from that call stays`() {
        val permissions = FakePermissions().apply { register(use) }
        assertThrows<IllegalStateException> { permissions.register(all, Permission("kits.use")) }
        assertEquals(listOf(use), permissions.registered)
    }

    @Test
    fun `a fake command source applies a descriptor's default unless it was granted the node`() {
        val steve = FakeCommandSource()
        val admin = Permission("kits.admin")
        assertTrue(steve.hasPermission(use))
        assertFalse(steve.hasPermission(admin))
        steve.grant("kits.admin")
        assertTrue(steve.hasPermission(admin))
        assertTrue(FakeCommandSource.console().hasPermission(Permission("kits.reset")))
    }
}
