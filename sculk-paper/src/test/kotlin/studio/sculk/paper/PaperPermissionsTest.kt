package studio.sculk.paper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionDefault
import org.bukkit.permissions.PermissionDefault as BukkitDefault

class PaperPermissionsTest {
    @Test
    fun `each default maps onto the Bukkit default with the same holders`() {
        val mapped = PermissionDefault.entries.associateWith { it.bukkit }
        assertEquals(
            mapOf(
                PermissionDefault.EVERYONE to BukkitDefault.TRUE,
                PermissionDefault.OPERATORS to BukkitDefault.OP,
                PermissionDefault.NON_OPERATORS to BukkitDefault.NOT_OP,
                PermissionDefault.NOBODY to BukkitDefault.FALSE,
            ),
            mapped,
        )
        PermissionDefault.entries.forEach { default ->
            listOf(true, false).forEach { operator -> assertEquals(default.appliesTo(operator), default.bukkit.getValue(operator)) }
        }
    }

    @Test
    fun `a descriptor becomes a Bukkit permission with its description and children`() {
        val descriptor = Permission(
            "shop.*",
            "Every shop permission",
            PermissionDefault.OPERATORS,
            mapOf("shop.buy" to true, "shop.debug" to false),
        )
        val native = descriptor.toBukkit()
        assertEquals("shop.*", native.name)
        assertEquals("Every shop permission", native.description)
        assertEquals(BukkitDefault.OP, native.default)
        assertEquals(mapOf("shop.buy" to true, "shop.debug" to false), native.children)
        assertEquals("", Permission("shop.buy").toBukkit().description, "Bukkit stores a missing description as empty")
    }
}
