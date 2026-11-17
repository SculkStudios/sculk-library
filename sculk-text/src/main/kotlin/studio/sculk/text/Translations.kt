package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.translation.Translator
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.net.URI
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

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
    /** The locale whose bundle answers when the viewer's own locale and language have no template. */
    public val defaultLocale: Locale,
    private val bundles: Map<Locale, Map<String, String>>,
    internal val text: Text,
) {
    /** Every locale with a bundle. */
    public val locales: Set<Locale> get() = bundles.keys

    // GlobalTranslator asks every source about every translatable component, vanilla keys included,
    // so the keys each bundle can translate, plural stems among them, are known before the first lookup.
    private val translatable: Map<Locale, Set<String>> = bundles.mapValues { (_, messages) -> messages.keys + pluralStems(messages.keys) }
    private val anyTranslatable: Set<String> = translatable.values.flatMapTo(HashSet()) { it }
    private val chains = ConcurrentHashMap<Locale, List<Locale>>()

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
    public fun translator(name: Key): Translator = TranslationsTranslator(name, text) { this }

    /** Whether [key] has a template in [locale]'s chain, itself or as plural forms. */
    internal fun has(key: String, locale: Locale): Boolean =
        key in anyTranslatable && chain(locale).any { translatable[it]?.contains(key) == true }

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

    /** The locales [locale] falls back through, keeping only those with a bundle. */
    private fun chain(locale: Locale): List<Locale> {
        chains[locale]?.let { return it }
        val chain = listOf(locale, Locale.of(locale.language), defaultLocale, Locale.of(defaultLocale.language))
            .distinct()
            .filter { it in bundles }
        // A client names its own locale; the cap keeps one that sends a new locale each time from growing the cache.
        if (chains.size < MAX_CACHED_CHAINS) chains.putIfAbsent(locale, chain)
        return chain
    }

    @SculkStable
    public companion object {
        private val FILE = Regex("""messages(?:_([a-z]{2,3})(?:_([A-Z]{2}))?)?\.properties""")
        private const val MAX_CACHED_CHAINS = 256

        /** The stem of each plural form in [keys]: `coins` for `coins.one`. */
        private fun pluralStems(keys: Set<String>): List<String> = keys.mapNotNull { key ->
            val category = PluralCategory.entries.firstOrNull { key.endsWith(".${it.suffix}") } ?: return@mapNotNull null
            key.substring(0, key.length - category.suffix.length - 1)
        }

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
                val bundles = readBundles(directory, defaultLocale)
                if (bundles.isEmpty()) {
                    return SculkFailure.InvalidInput(directory.toString(), "at least one messages_<language>.properties file", "none")
                }
                Translations(defaultLocale, bundles, text)
            }

        /**
         * Loads a plugin's own bundles from the directory [resources] on [classLoader], usually inside
         * the plugin jar, then lays the bundles in [overrides] over them key by key: an operator
         * changes one message by putting only that key in `overrides/messages_<language>.properties`,
         * and every other key, including keys a later plugin version adds, keeps its default.
         * [resources] must contain [defaultLocale]'s bundle (`messages_<language>[_<COUNTRY>]` or
         * `messages`); [overrides] may be missing or empty. Nothing is returned half-loaded: one
         * unreadable file fails the whole load.
         */
        public fun load(
            classLoader: ClassLoader,
            resources: String,
            overrides: Path,
            defaultLocale: Locale,
            text: Text = Text(),
        ): SculkResult<Translations> = SculkResult.catching("read the translations in $resources and $overrides") {
            // Jars need not contain directory entries, so the folder is found through the default
            // locale's own bundle, which must exist anyway.
            val folder = resources.trim('/')
            val names = defaultBundleNames(defaultLocale)
            val anchor = names.firstNotNullOfOrNull { classLoader.getResource("$folder/$it") }
                ?: return SculkFailure.InvalidInput(folder, "${names.joinToString(" or ")} on the classpath", "none of them")
            val defaults = withDirectory(anchor.toURI()) { file -> readBundles(file.parent, defaultLocale) }
            val edited = if (Files.isDirectory(overrides)) readBundles(overrides, defaultLocale) else emptyMap()
            val merged = (defaults.keys + edited.keys).associateWith { locale -> defaults[locale].orEmpty() + edited[locale].orEmpty() }
            Translations(defaultLocale, merged, text)
        }

        private fun readBundles(directory: Path, defaultLocale: Locale): Map<Locale, Map<String, String>> {
            val files = Files.list(directory).use { listing -> listing.toList() }.mapNotNull { file ->
                val (language, country) = FILE.matchEntire(file.name.trimEnd('/'))?.destructured ?: return@mapNotNull null
                val locale = when {
                    language.isEmpty() -> null
                    country.isEmpty() -> Locale.of(language)
                    else -> Locale.of(language, country)
                }
                locale to file
            }
            val bundles = mutableMapOf<Locale, Map<String, String>>()
            for ((locale, file) in files.sortedBy { (locale, _) -> locale != null }) {
                val target = locale ?: defaultLocale
                bundles[target] = bundles[target].orEmpty() + read(file)
            }
            return bundles
        }

        private fun defaultBundleNames(locale: Locale): List<String> = listOfNotNull(
            locale.country.takeIf { it.isNotEmpty() }?.let { "messages_${locale.language}_$it.properties" },
            "messages_${locale.language}.properties",
            "messages.properties",
        )

        /** Runs [block] on the file [uri] names, opening the jar it points into when it is one. */
        private fun <T> withDirectory(uri: URI, block: (Path) -> T): T {
            if (uri.scheme != "jar") return block(Path.of(uri))
            val opened = try {
                FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            } catch (_: FileSystemAlreadyExistsException) {
                null
            }
            return opened.use { block(Path.of(uri)) }
        }

        private fun read(file: Path): Map<String, String> {
            val properties = Properties().apply { Files.newBufferedReader(file, Charsets.UTF_8).use(::load) }
            return properties.stringPropertyNames().associateWith(properties::getProperty)
        }
    }
}
