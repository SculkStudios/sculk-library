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
