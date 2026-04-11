package gg.sculk.core.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandBuilderTest {
    @Test
    fun `command builder sets name correctly`() {
        val builder = command("test") {}
        assertEquals("test", builder.node.name)
    }

    @Test
    fun `command builder sets permission`() {
        val builder =
            command("test") {
                permission = "test.use"
            }
        assertEquals("test.use", builder.node.permission)
    }

    @Test
    fun `sub registers child node`() {
        val builder =
            command("test") {
                sub("reload") {}
                sub("info") {}
            }
        assertEquals(2, builder.node.subcommands.size)
        assertNotNull(builder.node.findSubcommand("reload"))
        assertNotNull(builder.node.findSubcommand("info"))
    }

    @Test
    fun `findSubcommand is case insensitive`() {
        val builder =
            command("test") {
                sub("Reload") {}
            }
        assertNotNull(builder.node.findSubcommand("reload"))
        assertNotNull(builder.node.findSubcommand("RELOAD"))
    }

    @Test
    fun `player executor is registered`() {
        val builder =
            command("test") {
                player { }
            }
        assertNotNull(builder.node.playerExecutor)
        assertNull(builder.node.consoleExecutor)
        assertNull(builder.node.anyExecutor)
    }

    @Test
    fun `executes registers any executor`() {
        val builder =
            command("test") {
                executes { }
            }
        assertNotNull(builder.node.anyExecutor)
        assertNull(builder.node.playerExecutor)
    }

    @Test
    fun `string argument registered`() {
        val builder =
            command("test") {
                string("name")
            }
        assertEquals(1, builder.node.arguments.size)
        assertEquals("name", builder.node.arguments[0].name)
    }

    @Test
    fun `nested subcommand tree`() {
        val builder =
            command("root") {
                sub("level1") {
                    sub("level2") {
                        executes { }
                    }
                }
            }
        val level1 = builder.node.findSubcommand("level1")
        assertNotNull(level1)
        assertNotNull(level1!!.findSubcommand("level2"))
    }
}
