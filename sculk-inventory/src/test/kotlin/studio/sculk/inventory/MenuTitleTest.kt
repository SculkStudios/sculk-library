package studio.sculk.inventory

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkExperimental

@OptIn(SculkExperimental::class)
class MenuTitleTest {
    private data class State(val owner: String, val page: Int)

    private val spec = menu<State>(MenuLayout.chest(3)) {
        title("<primary><owner>'s shop, page <page>") { state -> mapOf("owner" to state.owner, "page" to state.page) }
    }

    @Test
    fun `a title value that changes makes a different title, so the menu reopens`() {
        assertEquals(spec.title(State("Alex", 1)), spec.title(State("Alex", 1)))
        assertNotEquals(spec.title(State("Alex", 1)), spec.title(State("Alex", 2)))
    }

    @Test
    fun `a title value is shown as text, never parsed as markup`() {
        val title = spec.title(State("<red>Admin", 1))
        val rendered = spec.text.render(title.template, TagResolver.resolver(spec.text.placeholders(title.values)))
        assertEquals("<red>Admin's shop, page 1", PlainTextComponentSerializer.plainText().serialize(rendered))
    }
}
