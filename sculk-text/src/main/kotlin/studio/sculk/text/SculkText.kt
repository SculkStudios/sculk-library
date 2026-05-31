package studio.sculk.text

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import studio.sculk.adventure.parseMessage
import studio.sculk.annotation.SculkStable
import studio.sculk.config.yaml.YamlMapper
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Per-player localization backed by YAML message bundles.
 *
 * Bundles live in `<dataFolder>/lang/<language>.yml` (e.g. `lang/en.yml`, `lang/es.yml`) as flat
 * `key: "<minimessage template>"` entries. Messages resolve in the player's client language, falling
 * back to [defaultLanguage] and finally to the key itself.
 *
 * Placeholders use MiniMessage tag style (`<name>`); pluralization picks `key.one` / `key.other`
 * based on a count and substitutes `<count>`.
 *
 * ```kotlin
 * // lang/en.yml ->  welcome: "<green>Welcome, <name>!"
 * sculk.text.send(player, "welcome", "name" to player.name)
 * ```
 */
@SculkStable
public class SculkText
    @SculkInternalCtor
    internal constructor(
        private val langFolder: File,
        private val logger: Logger,
        private val defaultLanguage: String,
    ) {
        // language -> (key -> template)
        private val bundles = ConcurrentHashMap<String, Map<String, String>>()

        init {
            reload()
        }

        /** Reloads every `<language>.yml` bundle from disk. */
        @SculkStable
        public fun reload() {
            bundles.clear()
            val files = langFolder.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }
            files?.forEach { file ->
                val language = file.nameWithoutExtension.lowercase()
                runCatching { flatten(YamlMapper.loadRaw(file)) }
                    .onSuccess { bundles[language] = it }
                    .onFailure { logger.warning("[SculkText] Failed to load lang/${file.name}: ${it.message}") }
            }
        }

        /** Resolves the raw template for [key] in [language], falling back to the default language. */
        @SculkStable
        public fun template(
            language: String,
            key: String,
        ): String? = bundles[language.lowercase()]?.get(key) ?: bundles[defaultLanguage]?.get(key)

        /** Renders [key] for [language] into a Component, substituting [placeholders]. Falls back to the key. */
        @SculkStable
        public fun component(
            language: String,
            key: String,
            vararg placeholders: Pair<String, String>,
        ): Component {
            val template = template(language, key) ?: return Component.text(key)
            return parseMessage(substitute(template, placeholders.toMap()))
        }

        /** Renders [key] in [player]'s client language. */
        @SculkStable
        public fun component(
            player: Player,
            key: String,
            vararg placeholders: Pair<String, String>,
        ): Component = component(languageOf(player), key, *placeholders)

        /** Renders a pluralized [key] (`key.one` / `key.other`) for [count], substituting `<count>`. */
        @SculkStable
        public fun plural(
            player: Player,
            key: String,
            count: Int,
            vararg placeholders: Pair<String, String>,
        ): Component {
            val form = if (count == 1) "$key.one" else "$key.other"
            return component(languageOf(player), form, *placeholders, "count" to count.toString())
        }

        /** Sends the localized [key] to [player]. */
        @SculkStable
        public fun send(
            player: Player,
            key: String,
            vararg placeholders: Pair<String, String>,
        ): Unit = (player as Audience).sendMessage(component(player, key, *placeholders))

        /** The resolved language tag for [player] (client locale language, lowercased). */
        @SculkStable
        public fun languageOf(player: Player): String =
            runCatching { player.locale().language.lowercase() }
                .getOrDefault(defaultLanguage)
                .ifBlank { defaultLanguage }

        private fun substitute(
            template: String,
            placeholders: Map<String, String>,
        ): String = placeholders.entries.fold(template) { acc, (k, v) -> acc.replace("<$k>", v) }

        @Suppress("UNCHECKED_CAST")
        private fun flatten(
            raw: Map<String, Any?>,
            prefix: String = "",
        ): Map<String, String> {
            val out = linkedMapOf<String, String>()
            for ((key, value) in raw) {
                val full = if (prefix.isEmpty()) key else "$prefix.$key"
                when (value) {
                    is Map<*, *> -> out.putAll(flatten(value as Map<String, Any?>, full))
                    null -> {}
                    else -> out[full] = value.toString()
                }
            }
            return out
        }

        public companion object {
            /** Creates a [SculkText] loading bundles from `<dataFolder>/lang`. */
            @SculkStable
            @OptIn(SculkInternalCtor::class)
            public fun create(
                dataFolder: File,
                logger: Logger,
                defaultLanguage: String = Locale.ENGLISH.language,
            ): SculkText {
                val langFolder = File(dataFolder, "lang").apply { mkdirs() }
                return SculkText(langFolder, logger, defaultLanguage.lowercase())
            }
        }
    }

/** Opt-in marker guarding the internal [SculkText] constructor. */
@RequiresOptIn(message = "Use SculkText.create(...) instead of the internal constructor.")
@Retention(AnnotationRetention.BINARY)
internal annotation class SculkInternalCtor
