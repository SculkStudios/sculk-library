package studio.sculk.text

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.util.logging.Logger

@OptIn(SculkInternal::class)
class SculkBundlesTest {
    @TempDir
    lateinit var langFolder: File

    private val messages = SculkMessages(SculkTheme(mapOf("danger" to ThemeStyle.Solid("#ff5f5f"))))

    private fun bundles(default: String = "en") = SculkBundles(langFolder, messages, Logger.getLogger("test"), default)

    private fun write(name: String, content: String) {
        File(langFolder, name).writeText(content.trimIndent())
    }

    private fun plain(component: net.kyori.adventure.text.Component) = PlainTextComponentSerializer.plainText().serialize(component)

    @Test
    fun `nested keys are flattened with dots`() {
        write(
            "en.yml",
            """
            shop:
              bought: "Bought it"
              nested:
                deeper: "Deep"
            top: "Top"
            """,
        )

        val bundle = bundles()

        assertEquals("Bought it", bundle.template("en", "shop.bought"))
        assertEquals("Deep", bundle.template("en", "shop.nested.deeper"))
        assertEquals("Top", bundle.template("en", "top"))
    }

    @Test
    fun `a missing key renders as the key rather than as blank text`() {
        write("en.yml", "known: \"yes\"")

        assertEquals("shop.missing", plain(bundles().component("en", "shop.missing")))
    }

    @Test
    fun `an unknown language falls back to the default`() {
        write("en.yml", "greeting: \"Hello\"")

        assertEquals("Hello", plain(bundles().component("de", "greeting")))
    }

    @Test
    fun `a language with its own value wins over the default`() {
        write("en.yml", "greeting: \"Hello\"")
        write("de.yml", "greeting: \"Hallo\"")

        val bundle = bundles()

        assertEquals("Hallo", plain(bundle.component("de", "greeting")))
        assertEquals("Hello", plain(bundle.component("en", "greeting")))
    }

    @Test
    fun `bundle text goes through the theme`() {
        write("en.yml", "denied: \"<danger>No</danger>\"")

        val rendered = bundles().component("en", "denied")

        assertEquals("No", plain(rendered))
        assertEquals(0xff5f5f, rendered.children().firstOrNull()?.color()?.value() ?: rendered.color()?.value())
    }

    @Test
    fun `a placeholder value in a translated string is still inserted unparsed`() {
        write("en.yml", "joined: \"<name> joined\"")

        val rendered = bundles().component("en", "joined", "name" to "<danger>Impostor")

        assertEquals("<danger>Impostor joined", plain(rendered))
    }

    @Test
    fun `one malformed bundle does not take the others down with it`() {
        write("en.yml", "greeting: \"Hello\"")
        write("broken.yml", "this: [ unclosed")

        val bundle = bundles()

        assertTrue("en" in bundle.languages)
        assertTrue("broken" !in bundle.languages, "a bundle that failed to parse must not be registered")
        assertEquals("Hello", plain(bundle.component("en", "greeting")))
    }

    @Test
    fun `reload picks up a language added after construction and drops a removed one`() {
        write("en.yml", "greeting: \"Hello\"")
        val bundle = bundles()

        write("fr.yml", "greeting: \"Bonjour\"")
        bundle.reload()
        assertEquals("Bonjour", plain(bundle.component("fr", "greeting")))

        File(langFolder, "fr.yml").delete()
        bundle.reload()
        assertTrue("fr" !in bundle.languages)
        assertEquals("Hello", plain(bundle.component("fr", "greeting")), "a dropped language falls back")
    }

    @Test
    fun `a non-yml file in the lang folder is ignored`() {
        write("en.yml", "greeting: \"Hello\"")
        write("notes.txt", "not a bundle")

        assertEquals(setOf("en"), bundles().languages)
    }
}
