package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslationArgument
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.ResourceScope
import studio.sculk.getOrThrow
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** The translation example in docs/src/content/docs/guides/text.mdx, compiled and run. */
class TextGuideTest {
    @TempDir
    lateinit var dataPath: Path

    private fun Component.plain(locale: Locale): String =
        PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(this, locale))

    @Test
    fun `the guide's messages render per locale, take an operator override, and pluralise`() {
        val scope = ResourceScope.root("myplugin")
        val messages = TranslationFiles.load(
            classLoader = javaClass.classLoader,
            resources = "lang",
            overrides = dataPath.resolve("lang"),
            defaultLocale = Locale.ENGLISH,
        ).getOrThrow()
        messages.register(scope, Key.key("myplugin", "messages"))
        try {
            assertEquals("Welcome to the shop!", Component.translatable("shop.welcome").plain(Locale.ENGLISH))
            assertEquals("Welkom in de winkel!", Component.translatable("shop.welcome").plain(Locale.of("nl", "BE")))
            assertEquals("3 coins", Component.translatable("coins", TranslationArgument.numeric(3)).plain(Locale.ENGLISH))

            Files.createDirectories(dataPath.resolve("lang"))
            Files.writeString(dataPath.resolve("lang/messages_en.properties"), "shop.welcome=Hello, shopper\n")
            messages.reload().getOrThrow()
            assertEquals("Hello, shopper", Component.translatable("shop.welcome").plain(Locale.ENGLISH))
            assertEquals("1 coin", Component.translatable("coins", TranslationArgument.numeric(1)).plain(Locale.ENGLISH))
        } finally {
            scope.close()
        }
    }
}
