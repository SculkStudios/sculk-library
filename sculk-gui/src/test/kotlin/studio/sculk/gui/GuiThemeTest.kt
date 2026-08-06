package studio.sculk.gui

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import studio.sculk.annotation.SculkInternal
import studio.sculk.coroutine.SculkCoroutineScope
import studio.sculk.scheduler.FakeScheduler
import studio.sculk.text.SculkMessages
import studio.sculk.text.SculkTheme
import studio.sculk.text.ThemeStyle

/**
 * A menu's items must render through the theme of the registry that opens it.
 *
 * They did not. A `Gui` is defined by `gui { }` long before anything knows which [SculkMessages]
 * will open it, and items were built there and then — against a default renderer carrying
 * [SculkTheme.EMPTY]. So a semantic tag in an item name reached the player as literal text, while
 * the GUI *title*, which `Gui.buildInventory` renders with the real renderer, came out themed.
 *
 * Nothing threw and no test failed; it was invisible until somebody opened a menu and read it.
 */
@OptIn(SculkInternal::class)
class GuiThemeTest {
    private lateinit var server: ServerMock
    private lateinit var registry: MenuRegistry

    private val theme =
        SculkTheme(
            mapOf(
                "danger" to ThemeStyle.Solid("#ff5f5f"),
                "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
            ),
        )

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        val scheduler = FakeScheduler()
        registry = MenuRegistry(scheduler, SculkCoroutineScope(scheduler), SculkMessages(theme))
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    private fun plain(component: Component?) = component?.let { PlainTextComponentSerializer.plainText().serialize(it) }

    /**
     * The stack the GUI resolves for [player], read straight from the definition.
     *
     * Not read back out of the opened inventory: MockBukkit's `setItem` does not preserve data
     * components, so the display name round-trips to null and the assertion would be about the
     * mock rather than about the theme.
     */
    private fun resolved(menu: Gui, slot: Int, player: org.bukkit.entity.Player) =
        menu.items.getValue(slot).resolveStack(player, SculkMessages(theme))

    @Test
    fun `describe fills a slot from a config item without losing its properties`() {
        // Reading only `descriptor.material` and rebuilding by hand is the tempting shortcut, and it
        // silently discards everything else a server owner wrote in their menu config.
        val menu =
            gui("<danger>Menu") {
                size = 9
                item(0) {
                    describe(
                        studio.sculk.items.ItemDescriptor(
                            material = "diamond_sword",
                            name = "<danger>Configured",
                            lore = listOf("<danger>from config"),
                            amount = 3,
                            glint = true,
                        ),
                    )
                }
            }

        val stack = resolved(menu, 0, server.addPlayer())

        assertEquals(Material.DIAMOND_SWORD, stack.type)
        assertEquals(3, stack.amount, "amount is part of the descriptor and must survive")
        assertEquals(true, stack.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE), "so is glint")
        // Themed by the registry that opened the menu, not by whatever was in scope at gui { }.
        assertEquals("Configured", plain(stack.getData(DataComponentTypes.CUSTOM_NAME)))
        assertEquals(listOf("from config"), stack.getData(DataComponentTypes.LORE)?.lines()?.map(::plain))
    }

    @Test
    fun `a name written beside describe overrides the config item's own`() {
        // How a static config icon carries per-player text: the owner sets the look, the code sets
        // the line that changes.
        val menu =
            gui("<danger>Menu") {
                size = 9
                item(0) {
                    describe(studio.sculk.items.ItemDescriptor(material = "paper", name = "<danger>From config"))
                    name = "<danger>From code"
                }
            }

        assertEquals("From code", plain(resolved(menu, 0, server.addPlayer()).getData(DataComponentTypes.CUSTOM_NAME)))
    }

    @Test
    fun `a slot that supplies its own stack still takes the name and lore beside it`() {
        // The whole reason `stack(...)` exists is metadata the GUI defaults cannot express -- a
        // player skull carrying a profile, a config-backed ItemDescriptor. Returning that stack
        // untouched meant every one of those slots silently dropped the `name` and `lore(...)`
        // written beneath it, in a block that reads as though it sets both.
        //
        // Found in DaisyStaff, where it made every player head in every staff menu render as a bare
        // "Player Head": the reports queue, the appeals queue, history, alts and the punish menu.
        // Nothing threw, no test failed, and it was visible only by opening the menu.
        val player = server.addPlayer()
        val menu =
            gui("Title") {
                size = 27
                item(13) {
                    stack(org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD))
                    name = "<danger>Griefer"
                    lore("<value>Priority: high")
                }
            }

        val rendered = resolved(menu, 13, player)

        assertEquals("Griefer", plain(rendered.getData(DataComponentTypes.CUSTOM_NAME)))
        assertEquals(Material.PLAYER_HEAD, rendered.type, "the supplied stack must survive")
        val lore = rendered.getData(DataComponentTypes.LORE)?.lines()
        assertEquals(1, lore?.size)
        assertTrue(plain(lore!!.first())!!.contains("Priority: high"), plain(lore.first()))
    }

    @Test
    fun `a supplied stack with no name beside it is not given an empty one`() {
        // The other half of the fix: writing the name unconditionally would stamp an empty
        // component over whatever the caller had already baked into the stack, which loses the same
        // information by the opposite route.
        //
        // Asserted as "no component was written" rather than "the caller's name survived", because
        // the second is not this library's behaviour to assert -- MockBukkit's ItemStack.clone does
        // not carry data components, so a test phrased that way fails identically whether the fix
        // is present or not. It would have been a test of the mock.
        val player = server.addPlayer()
        val menu =
            gui("Title") {
                size = 27
                item(13) { stack(org.bukkit.inventory.ItemStack(Material.DIAMOND)) }
            }

        val rendered = resolved(menu, 13, player)

        assertEquals(Material.DIAMOND, rendered.type)
        assertEquals(null, rendered.getData(DataComponentTypes.CUSTOM_NAME))
        assertEquals(null, rendered.getData(DataComponentTypes.LORE))
    }

    @Test
    fun `an item name is rendered with the opening registry's theme`() {
        val player = server.addPlayer()
        val menu =
            gui("<danger>Title") {
                size = 27
                item(13) {
                    material = Material.DIAMOND
                    name = "<danger>Careful"
                }
            }

        val rendered = resolved(menu, 13, player).getData(DataComponentTypes.CUSTOM_NAME)

        assertEquals("Careful", plain(rendered))
        assertFalse(plain(rendered)!!.contains("danger"), "the theme tag reached the player as text")
    }

    @Test
    fun `item lore is rendered with the theme too`() {
        val player = server.addPlayer()
        val menu =
            gui("Menu") {
                size = 27
                item(0) {
                    material = Material.PAPER
                    lore += "<value>42"
                }
            }

        val lore = resolved(menu, 0, player).getData(DataComponentTypes.LORE)!!.lines()

        assertEquals(listOf("42"), lore.map { plain(it) })
    }

    @Test
    fun `the stack DSL is themed as well`() {
        // `stack { name(...) }` goes through ItemBuilder rather than GuiItemBuilder, and had its own
        // untheme-d renderer. It is the path a skull with a player's name uses.
        val player = server.addPlayer()
        val menu =
            gui("Menu") {
                size = 27
                item(4) {
                    stack {
                        material(Material.PLAYER_HEAD)
                        name("<danger>Owner")
                    }
                }
            }

        val rendered = resolved(menu, 4, player).getData(DataComponentTypes.CUSTOM_NAME)

        assertEquals("Owner", plain(rendered))
    }

    @Test
    fun `a per-player dynamic item is themed`() {
        val player = server.addPlayer()
        val menu =
            gui("Menu") {
                size = 27
                item(8) {
                    material = Material.COMPASS
                    dynamicContent { viewer -> name = "<danger>${viewer.name}" }
                }
            }

        val rendered = resolved(menu, 8, player).getData(DataComponentTypes.CUSTOM_NAME)

        assertEquals(player.name, plain(rendered))
    }

    @Test
    fun `a refreshed slot keeps the theme`() {
        // refresh() re-renders one slot mid-session; it must not fall back to the themeless renderer.
        val player = server.addPlayer()
        val menu =
            gui("Menu") {
                size = 27
                item(2) {
                    material = Material.EMERALD
                    name = "<value>Balance"
                }
            }

        val session = registry.open(menu, player)
        session.refresh(2)
        val rendered = resolved(menu, 2, player).getData(DataComponentTypes.CUSTOM_NAME)

        assertEquals("Balance", plain(rendered))
        assertTrue(!plain(rendered)!!.contains("value"))
    }

    @Test
    fun `an unthemed registry still renders plain text`() {
        // The default renderer is a valid choice; an unknown tag simply stays as written.
        val scheduler = FakeScheduler()
        val plainRegistry = MenuRegistry(scheduler, SculkCoroutineScope(scheduler), SculkMessages())
        val player = server.addPlayer()
        val menu =
            gui("Menu") {
                size = 27
                item(1) {
                    material = Material.STONE
                    name = "<gray>Plain"
                }
            }

        plainRegistry.open(menu, player)
        val rendered = menu.items.getValue(1).resolveStack(player, SculkMessages()).getData(DataComponentTypes.CUSTOM_NAME)

        assertEquals("Plain", plain(rendered))
    }
}
