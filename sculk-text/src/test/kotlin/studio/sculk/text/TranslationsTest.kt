package studio.sculk.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.writeText

class TranslationsTest {
    private val english = Locale.of("en")
    private val dutch = Locale.of("nl")
    private val belgian = Locale.of("nl", "BE")

    private val translations = Translations.of(
        english,
        mapOf(
            english to mapOf("greeting" to "Hello", "farewell" to "Bye", "coins.one" to "<count> coin", "coins.other" to "<count> coins"),
            dutch to
                mapOf(
                    "greeting" to "Hallo",
                    "coins.zero" to "geen munten",
                    "coins.one" to "<count> munt",
                    "coins.other" to "<count> munten",
                ),
            belgian to mapOf("greeting" to "Dag"),
        ),
    )

    @Test
    fun `lookups fall back from country to language to the default`() {
        assertEquals("Dag", translations.template("greeting", belgian))
        assertEquals("Hallo", translations.template("greeting", Locale.of("nl", "NL")))
        assertEquals("Bye", translations.template("farewell", belgian))
        assertEquals("Hello", translations.template("greeting", Locale.of("fr")))
    }

    @Test
    fun `a missing key renders as itself so the gap is visible`() {
        assertEquals("shop.title", translations.template("shop.title", dutch))
    }

    @Test
    fun `plural forms choose zero, one or other`() {
        assertEquals("geen munten", translations.plural("coins", 0, dutch))
        assertEquals("<count> munt", translations.plural("coins", 1, belgian))
        assertEquals("<count> munten", translations.plural("coins", 7, dutch))
        assertEquals("<count> coins", translations.plural("coins", 0, english))
    }

    @Test
    fun `bundles load from UTF-8 property files named by locale`(@TempDir directory: Path) {
        directory.resolve("messages.properties").writeText("greeting=Hello\n")
        directory.resolve("messages_nl.properties").writeText("greeting=Hallo, wereld — welkom\n")
        directory.resolve("notes.txt").writeText("ignored")
        val loaded = Translations.load(directory, english).getOrThrow()
        assertEquals(setOf(english, dutch), loaded.locales)
        assertEquals("Hallo, wereld — welkom", loaded.template("greeting", dutch))
    }

    @Test
    fun `an empty directory is reported, not silently accepted`(@TempDir directory: Path) {
        val failure = Translations.load(directory, english) as SculkFailure.InvalidInput
        assertEquals("none", failure.received)
    }
}
