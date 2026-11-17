package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslationArgument
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.ResourceScope
import studio.sculk.SculkFailure
import studio.sculk.getOrThrow
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class TranslationFilesTest {
    @TempDir
    lateinit var temp: Path

    private val plain = PlainTextComponentSerializer.plainText()
    private val english = Locale.of("en")
    private val dutch = Locale.of("nl")

    private val loaders = mutableListOf<URLClassLoader>()

    // A class loader keeps its jar open, and Windows refuses to delete an open file.
    @AfterEach
    fun closeLoaders() {
        loaders.forEach(URLClassLoader::close)
    }

    /** A plugin jar with [files] under `lang/` and no directory entries, loaded the way a server loads a plugin. */
    private fun pluginJar(vararg files: Pair<String, String>): ClassLoader {
        val jar = temp.resolve("plugin.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            files.forEach { (name, content) ->
                out.putNextEntry(JarEntry("lang/$name"))
                out.write(content.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        return URLClassLoader(arrayOf(jar.toUri().toURL()), null).also(loaders::add)
    }

    private fun overrides(vararg files: Pair<String, String>): Path = temp.resolve("data/lang").also { dir ->
        Files.createDirectories(dir)
        files.forEach { (name, content) -> Files.writeString(dir.resolve(name), content) }
    }

    @Test
    fun `defaults load from the plugin jar and an operator override replaces only the keys it names`() {
        val loader = pluginJar(
            "messages_en.properties" to "greeting=Hello\nfarewell=Bye\n",
            "messages_nl.properties" to "greeting=Hallo\n",
        )
        val dir = overrides("messages_en.properties" to "greeting=Welcome aboard\n")
        val translations = Translations.load(loader, "lang", dir, english).getOrThrow()
        assertEquals("Welcome aboard", translations.template("greeting", english))
        assertEquals("Bye", translations.template("farewell", english))
        assertEquals("Hallo", translations.template("greeting", dutch))
    }

    @Test
    fun `a missing overrides folder means the defaults are used as shipped`() {
        val loader = pluginJar("messages_en.properties" to "greeting=Hello\n")
        val translations = Translations.load(loader, "lang", temp.resolve("nowhere"), english).getOrThrow()
        assertEquals("Hello", translations.template("greeting", english))
    }

    @Test
    fun `a plugin without bundles at the named place fails instead of loading nothing`() {
        val loader = pluginJar("messages_en.properties" to "greeting=Hello\n")
        val result = Translations.load(loader, "messages", temp, english)
        assertTrue(result is SculkFailure.InvalidInput, "got $result")
    }

    @Test
    fun `a reload applies edited files, and a reload that fails keeps the last good translations`() {
        val loader = pluginJar("messages_en.properties" to "greeting=Hello\n")
        val dir = overrides()
        val files = TranslationFiles.load(loader, "lang", dir, english).getOrThrow()
        Files.writeString(dir.resolve("messages_en.properties"), "greeting=Hi there\n")
        files.reload().getOrThrow()
        assertEquals("Hi there", files.translations.template("greeting", english))

        Files.writeString(dir.resolve("messages_en.properties"), "greeting=\\uZZZZ\n")
        assertTrue(files.reload() is SculkFailure, "a malformed escape must fail the reload")
        assertEquals("Hi there", files.translations.template("greeting", english))
    }

    @Test
    fun `registered translations render per locale through Adventure until their scope closes`() {
        val loader = pluginJar(
            "messages_en.properties" to "greeting=Hello\ncoins.one=<count> coin\ncoins.other=<count> coins\n",
            "messages_nl.properties" to "greeting=Hallo\n",
        )
        val dir = overrides()
        val files = TranslationFiles.load(loader, "lang", dir, english).getOrThrow()
        val scope = ResourceScope.root("test")
        files.register(scope, Key.key("sculk-test", "messages"))
        val greeting = Component.translatable("greeting")
        try {
            assertEquals("Hallo", render(greeting, dutch))
            assertEquals("3 coins", render(Component.translatable("coins", TranslationArgument.numeric(3)), english))
            Files.writeString(dir.resolve("messages_nl.properties"), "greeting=Goedendag\n")
            files.reload().getOrThrow()
            assertEquals("Goedendag", render(greeting, dutch))
        } finally {
            scope.close()
        }
        assertEquals("greeting", render(greeting, dutch))
    }

    private fun render(component: Component, locale: Locale): String = plain.serialize(GlobalTranslator.render(component, locale))
}
