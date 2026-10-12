package studio.sculk.text

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
 * Message templates per locale. A lookup falls back from the exact locale (`nl_BE`) to its language
 * (`nl`) to [defaultLocale]; a key missing everywhere renders as itself so the gap is visible.
 *
 * Templates are trusted MiniMessage, like any template: translators may style them, players may not.
 */
@SculkStable
public class Translations private constructor(public val defaultLocale: Locale, private val bundles: Map<Locale, Map<String, String>>) {
    public val locales: Set<Locale> get() = bundles.keys

    /** The template for [key] in [locale], falling back as described above. */
    public fun template(key: String, locale: Locale = defaultLocale): String =
        chain(locale).firstNotNullOfOrNull { bundles[it]?.get(key) } ?: key

    /**
     * The plural form of [key] for [count]: `key.zero` when the count is zero and that form exists,
     * `key.one` for one, otherwise `key.other`. The template receives `<count>` as a value.
     */
    public fun plural(key: String, count: Long, locale: Locale = defaultLocale): String {
        val forms = buildList {
            if (count == 0L) add("$key.zero")
            if (count == 1L) add("$key.one")
            add("$key.other")
        }
        return forms.firstNotNullOfOrNull { form -> chain(locale).firstNotNullOfOrNull { bundles[it]?.get(form) } } ?: key
    }

    private fun chain(locale: Locale): List<Locale> =
        listOf(locale, Locale.of(locale.language), defaultLocale, Locale.of(defaultLocale.language)).distinct()

    @SculkStable
    public companion object {
        private val FILE = Regex("""messages(?:_([a-z]{2,3})(?:_([A-Z]{2}))?)?\.properties""")

        /** Translations built in code, for tests and plugins that ship messages as constants. */
        public fun of(defaultLocale: Locale, bundles: Map<Locale, Map<String, String>>): Translations =
            Translations(defaultLocale, bundles.mapValues { it.value.toMap() })

        /**
         * Loads every `messages_<language>[_<COUNTRY>].properties` file in [directory], read as
         * UTF-8. `messages.properties` belongs to [defaultLocale].
         */
        public fun load(directory: Path, defaultLocale: Locale): SculkResult<Translations> =
            SculkResult.catching("read the translations in $directory") {
                val bundles = Files.list(directory).use { files ->
                    files.toList().mapNotNull { file ->
                        val match = FILE.matchEntire(file.name) ?: return@mapNotNull null
                        val (language, country) = match.destructured
                        val locale = when {
                            language.isEmpty() -> defaultLocale
                            country.isEmpty() -> Locale.of(language)
                            else -> Locale.of(language, country)
                        }
                        locale to Properties().apply { file.reader(Charsets.UTF_8).use(::load) }.let { properties ->
                            properties.stringPropertyNames().associateWith(properties::getProperty)
                        }
                    }.toMap()
                }
                if (bundles.isEmpty()) {
                    return SculkFailure.InvalidInput(directory.toString(), "at least one messages_<language>.properties file", "none")
                }
                Translations(defaultLocale, bundles)
            }
    }
}
