package studio.sculk.command

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.permission.PermissionCheck
import studio.sculk.permission.PermissionContext
import studio.sculk.permission.PermissionDefault
import studio.sculk.permission.PermissionGroup
import studio.sculk.testkit.FakeCommandSource

object HomePermissions : PermissionGroup("homes") {
    val use = permission("use", "Teleport home", PermissionDefault.EVERYONE)
    val admin = permission("admin", "Manage other players' homes")
    val build = permission("build", "Set homes in the build world", PermissionDefault.EVERYONE)
}

class CommandPermissionTest {
    private val dispatcher = CommandDispatcher()
    private val steve = FakeCommandSource("Steve")
    private val console = FakeCommandSource.console()

    @Test
    fun `a descriptor's default decides for senders the platform sets nothing for`() = runTest {
        dispatcher.register(
            command("home") {
                permission(HomePermissions.use)
                executes { reply("<success>Welcome home.") }
                literal("clear") {
                    permission(HomePermissions.admin)
                    executes { reply("<success>Cleared.") }
                }
            },
        )
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "home"))
        assertTrue(dispatcher.execute(steve, "home clear") is CommandResult.UnknownCommand)
        assertEquals(CommandResult.Success, dispatcher.execute(console, "home clear"))
        steve.grant("homes.admin")
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "home clear"))
    }

    @Test
    fun `a contextual requirement follows the sender as it moves`() = runTest {
        val spec = command("sethome") {
            permission(HomePermissions.build.where(PermissionContext.WORLD, "minecraft:build"))
            executes { reply("<success>Home set.") }
        }
        dispatcher.register(spec)
        steve.permissionContext = PermissionContext.of(PermissionContext.WORLD to "minecraft:overworld")
        assertTrue(dispatcher.execute(steve, "sethome") is CommandResult.UnknownCommand)
        steve.permissionContext = PermissionContext.of(PermissionContext.WORLD to "minecraft:build")
        assertEquals(CommandResult.Success, dispatcher.execute(steve, "sethome"))
        assertEquals(setOf(PermissionContext.WORLD), spec.root.requirement.check?.contextKeys)
    }

    @Test
    fun `a permission string still declares a raw node`() {
        val spec = command("ban") {
            permission = "mod.ban"
            executes {}
        }
        assertEquals("mod.ban", spec.root.requirement.permission)
        assertEquals(PermissionCheck.node("mod.ban"), spec.root.requirement.check)
        assertEquals(emptyList<String>(), spec.usage(steve))
        assertEquals(listOf("/ban"), spec.usage(console))
    }

    @Test
    fun `the last permission declared for a branch is the one that applies`() {
        val spec = command("warp") {
            permission = "warp.old"
            permission(HomePermissions.use)
            executes {}
        }
        assertEquals(HomePermissions.use, spec.root.requirement.check)
        assertEquals("homes.use", spec.root.requirement.permission)
    }
}
