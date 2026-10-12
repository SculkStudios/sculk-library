package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslationArgument
import net.kyori.adventure.text.minimessage.translation.Argument
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.writeText

class TranslationsTest {
    private val plain = PlainTextComponentSerializer.plainText()
    private val english = Locale.of("en")
    private val dutch = Locale.of("nl")
    private val belgian = Locale.of("nl", "BE")
    private val polish = Locale.of("pl")

    private val translations = Translations.of(
        english,
        mapOf(
            english to mapOf(
                "greeting" to "Hello",
                "farewell" to "Bye",
                "welcome" to "Welcome, <name>",
                "coins.one" to "<count> coin",
                "coins.other" to "<count> coins",
            ),
            dutch to mapOf("greeting" to "Hallo", "coins.one" to "<count> munt", "coins.other" to "<count> munten"),
            belgian to mapOf("greeting" to "Dag"),
            polish to mapOf("coins.one" to "<count> moneta", "coins.few" to "<count> monety", "coins.many" to "<count> monet"),
        ),
        Text(Theme.of("primary" to ThemeStyle.Solid("#112233"))),
    )

    private fun Component.text(): String = plain.serialize(this)

    @Test
    fun `lookups fall back from country to language to the default`() {
        assertEquals("Dag", translations.template("greeting", belgian))
        assertEquals("Hallo", translations.template("greeting", Locale.of("nl", "NL")))
        assertEquals("Bye", translations.template("farewell", belgian))
        assertEquals("Hello", translations.template("greeting", Locale.of("fr")))
        assertNull(translations.template("shop.title", dutch))
    }

    @Test
    fun `values render as text, never as markup`() {
        assertEquals("Welcome, <red>Admin", translations.render("welcome", english, "name" to "<red>Admin").text())
    }

    @Test
    fun `a missing key renders as its own name in plain text`() {
        val rendered = translations.render("<red><click:run_command:/op me>x", english)
        assertEquals("<red><click:run_command:/op me>x", rendered.text())
        assertNull(rendered.color())
        assertNull(rendered.clickEvent())
    }

    @Test
    fun `plurals choose the form the locale's rules ask for and fall back to other`() {
        assertEquals("1 munt", translations.plural("coins", 1, belgian).text())
        assertEquals("7 munten", translations.plural("coins", 7, dutch).text())
        assertEquals("0 coins", translations.plural("coins", 0, english).text())
        assertEquals("3 monety", translations.plural("coins", 3, polish).text())
        assertEquals("5 monet", translations.plural("coins", 5, polish).text())
        assertEquals("22 monety", translations.plural("coins", 22, polish).text())
    }

    @Test
    fun `a locale without the key's forms falls back to the next bundle that has them, and a key with none renders as its name`() {
        assertEquals("2 coins", translations.plural("coins", 2, Locale.of("fr")).text())
        assertEquals("rewards", translations.plural("rewards", 2, english).text())
    }

    @Test
    fun `the Adventure translator renders per locale with templates parsed and arguments not`() {
        val translator = translations.translator(Key.key("sculk", "test"))
        val welcome = Component.translatable("welcome", Argument.component("name", Component.text("<red>Admin")))
        assertEquals("Welcome, <red>Admin", translator.translate(welcome, english)?.text())
        assertEquals("Dag", translator.translate(Component.translatable("greeting"), belgian)?.text())
        assertEquals("3 monety", translator.translate(Component.translatable("coins", TranslationArgument.numeric(3)), polish)?.text())
        assertNull(translator.translate(Component.translatable("missing"), english))
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
    fun `the default locale's own file wins over messages properties key by key`(@TempDir directory: Path) {
        directory.resolve("messages.properties").writeText("greeting=base\nfarewell=base bye\n")
        directory.resolve("messages_en.properties").writeText("greeting=explicit\n")
        val loaded = Translations.load(directory, english).getOrThrow()
        assertEquals("explicit", loaded.template("greeting", english))
        assertEquals("base bye", loaded.template("farewell", english))
    }

    @Test
    fun `an empty directory is reported, not silently accepted`(@TempDir directory: Path) {
        val failure = Translations.load(directory, english) as SculkFailure.InvalidInput
        assertEquals("none", failure.received)
    }
}
