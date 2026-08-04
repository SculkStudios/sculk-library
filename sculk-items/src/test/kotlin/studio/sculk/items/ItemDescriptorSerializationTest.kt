package studio.sculk.items

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The descriptor decoded the way a config actually decodes it.
 *
 * `sculk-items` cannot depend on `sculk-config` — the dependency runs the other way — so nothing
 * else proves the two agree. The engine below is configured identically to `SculkConfig`'s on
 * purpose: a naming strategy that differed would rename every key in every shipped file, and it
 * would surface as a server owner's populated config reading back as defaults rather than as a
 * failure anybody could see.
 */
class ItemDescriptorSerializationTest {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = true,
            strictMode = false,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
            sequenceBlockIndent = 2,
            yamlNamingStrategy = YamlNamingStrategy.KebabCase,
        ),
    )

    /** The shape a plugin actually writes: a descriptor as a property of its own settings class. */
    @Serializable
    private data class Settings(val icon: ItemDescriptor = ItemDescriptor(material = "poppy"))

    @Test
    fun `a descriptor survives a round trip through the config engine`() {
        val descriptor = ItemDescriptor(
            material = "diamond_sword",
            name = "<aqua>Starter Sword",
            lore = listOf("<gray>A clean starter weapon."),
            amount = 2,
            enchantments = mapOf("sharpness" to 5),
            glint = true,
            model = "sculk:starter_sword",
            customModelData = 1001,
            hideVanillaTooltip = true,
            unbreakable = true,
            data = mapOf("starter_item" to "true"),
        )

        val rendered = yaml.encodeToString(ItemDescriptor.serializer(), descriptor)

        assertEquals(descriptor, yaml.decodeFromString(ItemDescriptor.serializer(), rendered))
    }

    @Test
    fun `multi word properties are written as the kebab case a server owner edits`() {
        val rendered = yaml.encodeToString(
            ItemDescriptor.serializer(),
            ItemDescriptor(material = "poppy", customModelData = 7, hideVanillaTooltip = true),
        )

        assertTrue(rendered.contains("custom-model-data: 7"), rendered)
        assertTrue(rendered.contains("hide-vanilla-tooltip: true"), rendered)
        // The camelCase forms would silently never match a key in a generated file.
        assertTrue(!rendered.contains("customModelData"), rendered)
        assertTrue(!rendered.contains("hideVanillaTooltip"), rendered)
    }

    @Test
    fun `a descriptor nested in another serializable class decodes`() {
        val settings = Settings(icon = ItemDescriptor(material = "sunflower", amount = 3))
        val rendered = yaml.encodeToString(Settings.serializer(), settings)

        assertEquals(settings, yaml.decodeFromString(Settings.serializer(), rendered))
    }

    @Test
    fun `an omitted descriptor falls back to the default the class declares`() {
        // What makes "the defaults are the shipped file" work: an empty document is decodable.
        assertEquals(Settings(), yaml.decodeFromString(Settings.serializer(), "{}"))
    }

    @Test
    fun `a key the descriptor does not model is ignored rather than fatal`() {
        // strictMode = false is why a config written by a newer version still boots on an older one.
        val decoded = yaml.decodeFromString(ItemDescriptor.serializer(), "material: poppy\nnot-a-field: 1\n")

        assertEquals("poppy", decoded.material)
    }
}
