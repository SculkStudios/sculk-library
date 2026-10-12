package studio.sculk.permission

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import studio.sculk.testkit.FakePermissionSubject

object ShopPermissions : PermissionGroup("shop") {
    val buy = permission("buy", "Buy from the shop", PermissionDefault.EVERYONE)
    val sell = permission("sell", "Sell to the shop", PermissionDefault.EVERYONE)
    val admin = permission("admin", "Change prices and stock")
    val all = permission("*", "Every shop permission", children = listOf(buy, sell, admin))
}

class PermissionTest {
    @Test
    fun `a node is lower-case words joined by dots, optionally ending in a wildcard`() {
        listOf("shop", "shop.buy", "my-plugin.home_set.2", "shop.*").forEach { assertTrue(Permission.isValidNode(it), it) }
        listOf("", "Shop.buy", "shop..buy", "shop.", ".shop", "shop.*.buy", "*", "shop buy", "shop.b*").forEach {
            assertFalse(Permission.isValidNode(it), it)
        }
        val failure = assertThrows<IllegalArgumentException> { Permission("Shop.Buy") }
        assertEquals(
            "Permission node 'Shop.Buy' must be lower-case words of letters, digits, '-' or '_' joined by '.', optionally ending in '.*'.",
            failure.message,
        )
    }

    @Test
    fun `children are validated and a permission cannot be its own child`() {
        assertThrows<IllegalArgumentException> { Permission("shop.all", children = mapOf("Shop.Buy" to true)) }
        assertThrows<IllegalArgumentException> { Permission("shop.all", children = mapOf("shop.all" to true)) }
    }

    @Test
    fun `each default decides for operators and for everyone else`() {
        val table = PermissionDefault.entries.associateWith { listOf(it.appliesTo(operator = true), it.appliesTo(operator = false)) }
        assertEquals(
            mapOf(
                PermissionDefault.EVERYONE to listOf(true, true),
                PermissionDefault.OPERATORS to listOf(true, false),
                PermissionDefault.NON_OPERATORS to listOf(false, true),
                PermissionDefault.NOBODY to listOf(false, false),
            ),
            table,
        )
    }

    @Test
    fun `a group prefixes its nodes and lists them in declaration order`() {
        assertEquals(listOf("shop.buy", "shop.sell", "shop.admin", "shop.*"), ShopPermissions.permissions.map { it.node })
        assertEquals(mapOf("shop.buy" to true, "shop.sell" to true, "shop.admin" to true), ShopPermissions.all.children)
        assertEquals(PermissionDefault.OPERATORS, ShopPermissions.admin.default)
    }

    @Test
    fun `a group refuses a name declared twice and a prefix that is not a node`() {
        val failure = assertThrows<IllegalStateException> {
            object : PermissionGroup("dupe") {
                val first = permission("home")
                val second = permission("home")
            }
        }
        assertEquals("dupe.home is declared twice in the dupe group.", failure.message)
        assertThrows<IllegalArgumentException> { object : PermissionGroup("Bad Prefix") {} }
        assertThrows<IllegalArgumentException> { object : PermissionGroup("shop.*") {} }
    }

    @Test
    fun `a descriptor applies its default where the subject sets nothing`() {
        val player = FakePermissionSubject()
        val operator = FakePermissionSubject(operator = true)
        assertTrue(player.hasPermission(ShopPermissions.buy))
        assertFalse(player.hasPermission(ShopPermissions.admin))
        assertTrue(operator.hasPermission(ShopPermissions.admin))
        assertFalse(FakePermissionSubject(permissions = mapOf("shop.buy" to false)).hasPermission(ShopPermissions.buy))
    }

    @Test
    fun `a contextual permission needs the permission and an allowed value for every key`() {
        val netherFlight = Permission("travel.fly", default = PermissionDefault.EVERYONE)
            .where(PermissionContext.WORLD, "minecraft:the_nether", "minecraft:the_end")
            .where(PermissionContext.GAME_MODE, "survival")
        fun at(world: String) = PermissionContext.of(PermissionContext.WORLD to world, PermissionContext.GAME_MODE to "survival")
        val player = FakePermissionSubject(context = at("minecraft:the_nether"))
        assertTrue(player.hasPermission(netherFlight))
        player.permissionContext = at("minecraft:overworld")
        assertFalse(player.hasPermission(netherFlight))
        assertFalse(FakePermissionSubject().hasPermission(netherFlight), "a subject with no world fails a world condition")
        assertEquals(setOf(PermissionContext.WORLD, PermissionContext.GAME_MODE), netherFlight.contextKeys)
    }

    @Test
    fun `a contextual permission still needs the permission itself`() {
        val build = Permission("build.place").where(PermissionContext.GAME_MODE, "creative")
        val creative = PermissionContext.of(PermissionContext.GAME_MODE to "creative")
        assertFalse(FakePermissionSubject(context = creative).hasPermission(build))
        assertTrue(FakePermissionSubject(permissions = mapOf("build.place" to true), context = creative).hasPermission(build))
    }

    @Test
    fun `one key constrained twice is refused`() {
        val failure = assertThrows<IllegalArgumentException> {
            ShopPermissions.buy.where(PermissionContext.WORLD, "minecraft:overworld").where(PermissionContext.WORLD, "minecraft:the_end")
        }
        assertEquals("shop.buy already constrains world; list every allowed value in one where().", failure.message)
        assertThrows<IllegalArgumentException> { ShopPermissions.buy.where(PermissionContext.WORLD) }
    }

    @Test
    fun `a raw node is checked exactly as the platform checks a string`() {
        val check = PermissionCheck.node("Essentials.Fly")
        assertTrue(check.test(FakePermissionSubject(permissions = mapOf("Essentials.Fly" to true))))
        assertFalse(check.test(FakePermissionSubject()))
        assertTrue(check.test(FakePermissionSubject(operator = true)), "an unregistered node is held by operators, as on Bukkit")
        assertThrows<IllegalArgumentException> { PermissionCheck.node(" ") }
    }

    @Test
    fun `descriptors with the same declaration are equal`() {
        assertEquals(Permission("shop.buy", "Buy", PermissionDefault.EVERYONE), Permission("shop.buy", "Buy", PermissionDefault.EVERYONE))
        assertNotEquals(Permission("shop.buy"), Permission("shop.buy", default = PermissionDefault.EVERYONE))
        assertEquals(ShopPermissions.buy.where("world", "a"), ShopPermissions.buy.where("world", "a"))
    }

    @Test
    fun `a context keeps only the values it has`() {
        val context = PermissionContext.of(PermissionContext.WORLD to "minecraft:overworld", PermissionContext.GAME_MODE to null)
        assertEquals(setOf(PermissionContext.WORLD), context.keys)
        assertEquals("minecraft:overworld", context[PermissionContext.WORLD])
        assertEquals(null, context[PermissionContext.SERVER])
    }
}
