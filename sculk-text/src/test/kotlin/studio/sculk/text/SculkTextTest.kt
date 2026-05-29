package studio.sculk.text

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Logger

class SculkTextTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `resolves placeholders and falls back across languages`(
        @TempDir dir: File,
    ) {
        File(dir, "lang").mkdirs()
        File(dir, "lang/en.yml").writeText(
            """
            welcome: "Welcome, <name>!"
            only-en: "English only"
            apples:
              one: "<count> apple"
              other: "<count> apples"
            """.trimIndent(),
        )
        File(dir, "lang/es.yml").writeText(
            """
            welcome: "Bienvenido, <name>!"
            """.trimIndent(),
        )

        val text = SculkText.create(dir, Logger.getAnonymousLogger(), defaultLanguage = "en")

        assertEquals("Welcome, Steve!", plain.serialize(text.component("en", "welcome", "name" to "Steve")))
        assertEquals("Bienvenido, Alex!", plain.serialize(text.component("es", "welcome", "name" to "Alex")))
        // Missing in es -> falls back to en
        assertEquals("English only", plain.serialize(text.component("es", "only-en")))
        // Unknown key -> the key itself
        assertEquals("nope", plain.serialize(text.component("en", "nope")))
    }

    @Test
    fun `pluralization picks one or other and substitutes count`(
        @TempDir dir: File,
    ) {
        File(dir, "lang").mkdirs()
        File(dir, "lang/en.yml").writeText(
            """
            apples:
              one: "<count> apple"
              other: "<count> apples"
            """.trimIndent(),
        )
        val text = SculkText.create(dir, Logger.getAnonymousLogger(), defaultLanguage = "en")

        assertEquals("1 apple", plain.serialize(text.component("en", "apples.one", "count" to "1")))
        assertEquals("5 apples", plain.serialize(text.component("en", "apples.other", "count" to "5")))
    }
}
