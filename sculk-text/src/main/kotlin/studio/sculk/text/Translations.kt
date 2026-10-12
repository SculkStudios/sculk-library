package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.translation.Translator
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Properties
import kotlin.io.path.name
import kotlin.io.path.reader

/**
 * Message templates per locale, rendered through a [Text]. A lookup falls back from the exact locale
 * (`nl_BE`) to its language (`nl`) to [defaultLocale] and its language.
 *
 * Templates are trusted MiniMessage, like any template: translators may style them and use theme
 * styles, players may not. A key no bundle has renders as its own name in plain text, so the gap is
 * visible and a key built from player input cannot inject markup.
 */
@SculkStable
public class Translations private constructor(
    public val defaultLocale: Locale,
    private val bundles: Map<Locale, Map<String, String>>,
    private val text: Text,
) {
    public val locales: Set<Locale> get() = bundles.keys

    /** The template for [key] in [locale] after fallback, or null when no bundle has it. */
    public fun template(key: String, locale: Locale = defaultLocale): String? = chain(locale).firstNotNullOfOrNull { bundles[it]?.get(key) }

    /** Renders [key] for [locale] with [values] as placeholders, exactly as [Text.render] does. */
    public fun render(key: String, locale: Locale, vararg values: Pair<String, Any?>): Component =
        template(key, locale)?.let { text.render(it, *values) } ?: Component.text(key)

    /**
     * Renders the form of [key] that [count] takes in [locale]: `key.one`, `key.few` and so on by
     * [PluralCategory], falling back to `key.other`. The template receives `<count>` as a value.
     */
    public fun plural(key: String, count: Long, locale: Locale, vararg values: Pair<String, Any?>): Component =
        pluralForm(key, count, locale)?.let { form -> text.render(form.template, "count" to count, *values) } ?: Component.text(key)

    /**
     * These translations as an Adventure [Translator] named [name], for
     * `GlobalTranslator.translator().addSource(...)`, so a `Component.translatable(key, ...)` renders
     * in each viewer's own locale. Arguments are values, never parsed: positional ones fill `<arg:0>`
     * and the MiniMessage `Argument` helpers fill named tags. A key with plural forms takes its count
     * from its first numeric argument, available to the template as `<count>`.
     */
    public fun translator(name: Key): Translator = TranslationsTranslator(name, this, text)

    internal fun expandedTemplate(key: String, locale: Locale): String? = template(key, locale)?.let(text::expand)

    /** The form of [key] for [count] by each bundle's own rules, from the first bundle in the chain with that form or `other`. */
    internal fun pluralForm(key: String, count: Long, locale: Locale): PluralForm? {
        val other = "$key.${PluralCategory.OTHER.suffix}"
        for (candidate in chain(locale)) {
            val bundle = bundles[candidate] ?: continue
            val form = "$key.${PluralCategory.of(count, candidate).suffix}"
            bundle[form]?.let { return PluralForm(form, it) }
            bundle[other]?.let { return PluralForm(other, it) }
        }
        return null
    }

    internal class PluralForm(val key: String, val template: String)

    private fun chain(locale: Locale): List<Locale> =
        listOf(locale, Locale.of(locale.language), defaultLocale, Locale.of(defaultLocale.language)).distinct()

    @SculkStable
    public companion object {
        private val FILE = Regex("""messages(?:_([a-z]{2,3})(?:_([A-Z]{2}))?)?\.properties""")

        /** Translations built in code, for tests and plugins that ship messages as constants. */
        public fun of(defaultLocale: Locale, bundles: Map<Locale, Map<String, String>>, text: Text = Text()): Translations =
            Translations(defaultLocale, bundles.mapValues { it.value.toMap() }, text)

        /**
         * Loads every `messages_<language>[_<COUNTRY>].properties` file in [directory], read as UTF-8.
         * `messages.properties` also belongs to [defaultLocale]; where both it and the default
         * locale's own file have a key, the locale's own file wins.
         */
        public fun load(directory: Path, defaultLocale: Locale, text: Text = Text()): SculkResult<Translations> =
            SculkResult.catching("read the translations in $directory") {
                val files = Files.list(directory).use { listing -> listing.toList() }.mapNotNull { file ->
                    val (language, country) = FILE.matchEntire(file.name)?.destructured ?: return@mapNotNull null
                    val locale = when {
                        language.isEmpty() -> null
                        country.isEmpty() -> Locale.of(language)
                        else -> Locale.of(language, country)
                    }
                    locale to file
                }
                if (files.isEmpty()) {
                    return SculkFailure.InvalidInput(directory.toString(), "at least one messages_<language>.properties file", "none")
                }
                val bundles = mutableMapOf<Locale, Map<String, String>>()
                for ((locale, file) in files.sortedBy { (locale, _) -> locale != null }) {
                    val target = locale ?: defaultLocale
                    bundles[target] = bundles[target].orEmpty() + read(file)
                }
                Translations(defaultLocale, bundles, text)
            }

        private fun read(file: Path): Map<String, String> {
            val properties = Properties().apply { file.reader(Charsets.UTF_8).use(::load) }
            return properties.stringPropertyNames().associateWith(properties::getProperty)
        }
    }
}
