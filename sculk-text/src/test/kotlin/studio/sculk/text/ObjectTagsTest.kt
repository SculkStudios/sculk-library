package studio.sculk.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkExperimental

@OptIn(SculkExperimental::class)
class ObjectTagsTest {
    private val plain = PlainTextComponentSerializer.plainText()
    private val text = Text()

    private fun Component.flatten(): List<Component> = listOf(this) + children().flatMap { it.flatten() }

    @Test
    fun `sprite and head tags never print as text`() {
        val rendered = plain.serialize(text.render("<sprite:items:item/diamond> Gems for <head:Notch> <name>", "name" to "Alex"))
        assertFalse("<sprite" in rendered || "<head" in rendered, rendered)
        assertTrue("Gems for" in rendered && rendered.endsWith("Alex"), rendered)
    }

    @Test
    fun `where Adventure has object components a sprite tag becomes one`() {
        assumeTrue(Text.supportsObjects)
        val rendered = text.render("<sprite:items:item/diamond>")
        assertTrue(rendered.flatten().any { it.javaClass.name.contains("ObjectComponent") }, "$rendered")
    }

    @Test
    fun `where Adventure has no object components the tags render as nothing`() {
        assumeFalse(Text.supportsObjects)
        assertEquals("Gem", plain.serialize(text.render("<sprite:items:item/diamond>Gem<head:Notch>")))
    }

    @Test
    fun `a value cannot inject a sprite or head tag`() {
        assertEquals("<head:Notch>", plain.serialize(text.render("<name>", "name" to "<head:Notch>")))
    }
}
