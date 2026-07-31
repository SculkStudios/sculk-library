package studio.sculk.text

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Per-player localisation backed by YAML message bundles.
 *
 * Bundles live in `<dataFolder>/lang/<language>.yml`. Nested keys are flattened with dots, so
 * either shape works and a translator can group related strings:
 *
 * ```yaml
 * shop:
 *   bought: "<success>Bought <value><item></value>."
 *   too-poor: "<danger>You need <value><cost></value> more."
 * ```
 *
 * Every message renders through [SculkMessages], so bundles inherit the theme and the unparsed
 * placeholder boundary without doing anything — which matters more here than anywhere, because a
 * translated string is the most likely place for a `<name>` placeholder to meet a hostile value.
 *
 * A missing key renders as the key itself rather than as blank text: a visible `shop.bought` in
 * chat gets reported, and an empty line does not.
 */
@SculkStable
public class SculkBundles
@SculkInternal
constructor(
    private val langFolder: File,
    private val messages: SculkMessages,
    private val logger: Logger,
    private val defaultLanguage: String = "en",
) {
    private val bundles = ConcurrentHashMap<String, Map<String, String>>()

    init {
        reload()
    }

    /** Rereads every `<language>.yml` from disk. */
    @SculkStable
    public fun reload() {
        val loaded = HashMap<String, Map<String, String>>()
        val files = langFolder.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }
        files?.forEach { file ->
            runCatching { flatten(Yaml.default.parseToYamlNode(file.readText())) }
                .onSuccess { loaded[file.nameWithoutExtension.lowercase()] = it }
                // One malformed bundle must not take the others down with it; a server that adds a
                // half-finished translation should lose that language, not all of them.
                .onFailure { logger.warning("[SculkText] Could not read lang/${file.name}: ${it.message}") }
        }
        bundles.keys.retainAll(loaded.keys)
        bundles.putAll(loaded)
    }

    /** The languages with a loaded bundle. */
    @SculkStable
    public val languages: Set<String> get() = bundles.keys

    /** The raw template for [key], falling back to [defaultLanguage], or null if neither has it. */
    @SculkStable
    public fun template(language: String, key: String): String? =
        bundles[language.lowercase()]?.get(key) ?: bundles[defaultLanguage]?.get(key)

    @SculkStable
    public fun component(language: String, key: String, vararg values: Pair<String, String>): Component {
        val template = template(language, key) ?: return Component.text(key)
        return messages.render(template, *values)
    }

    /** Renders [key] in [player]'s client language. */
    @SculkStable
    public fun component(player: Player, key: String, vararg values: Pair<String, String>): Component =
        component(languageOf(player), key, *values)

    /**
     * Renders `key.one` or `key.other` depending on [count], with `<count>` available.
     *
     * Deliberately only two forms. Languages with more (Polish, Arabic) need a real plural-rule
     * engine, and half of one that silently picks the wrong form is worse than a translator
     * writing the awkward-but-correct string into `other`.
     */
    @SculkStable
    public fun plural(player: Player, key: String, count: Int, vararg values: Pair<String, String>): Component {
        val form = if (count == 1) "$key.one" else "$key.other"
        return component(languageOf(player), form, *values, "count" to count.toString())
    }

    @SculkStable
    public fun send(player: Player, key: String, vararg values: Pair<String, String>) {
        (player as Audience).sendMessage(component(player, key, *values))
    }

    private fun languageOf(player: Player): String = player.locale().language.lowercase()

    private fun flatten(node: YamlNode): Map<String, String> {
        val flat = LinkedHashMap<String, String>()
        collect(node, prefix = "", into = flat)
        return flat
    }

    private fun collect(node: YamlNode, prefix: String, into: MutableMap<String, String>) {
        when (node) {
            is YamlScalar -> if (prefix.isNotEmpty()) into[prefix] = node.content

            is YamlMap -> node.entries.forEach { (key, value) ->
                val name = key.content
                collect(value, if (prefix.isEmpty()) name else "$prefix.$name", into)
            }

            else -> Unit
        }
    }
}
