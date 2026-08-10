package studio.sculk.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlNamingStrategy
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Merging a new key into a file that contains a block sequence.
 *
 * This produced an unparseable file on a real server, and the failure is worth spelling out because
 * nothing about it is obvious from the code.
 *
 * `KEY_LINE` matched `- id: x`: a dash is not a space, a `#` or a `:`. So a sequence entry read as a
 * key called `- id`. YAML also permits a sequence to sit at its parent's indent, and the path stack
 * pops any entry at or above the current indent — so `sites:` was popped before `- id` was pushed and
 * the path came out `vote-menu.- id`. Older output indented its sequences, giving
 * `vote-menu.sites.- id`. The two never matched, the whole list looked absent, and a second copy was
 * appended at an indent where a block sequence has no owning key:
 *
 *     a block sequence may not be used as an implicit map key
 *
 * It stayed hidden because `appendMissing` returns the original untouched when nothing is missing.
 * It only fires the first time a release adds a key to a file that has a list in it.
 */
class ConfigMergeSequenceTest {
    @Serializable
    private data class Site(
        val id: String,
        val slot: Int,
        // Added in the newer version; absent from the file on disk.
        val cooldown: String = "",
    )

    @Serializable
    private data class Menu(
        val title: String = "Vote",
        val sites: List<Site> = emptyList(),
        // Added in the newer version.
        val buttons: Map<String, Int> = emptyMap(),
    )

    @Serializable
    @ConfigFile("menus.yml")
    private data class Root(val voteMenu: Menu = Menu())

    private val yaml =
        Yaml(
            configuration =
            YamlConfiguration(
                encodeDefaults = true,
                strictMode = false,
                yamlNamingStrategy = YamlNamingStrategy.KebabCase,
            ),
        )

    /** What the previous version wrote: sequence entries indented under their key. */
    private val onDisk =
        """
        vote-menu:
          title: Vote
          sites:
            - id: minecraftservers
              slot: 20
            - id: topg
              slot: 23
        """.trimIndent()

    @Test
    fun `a new key merges into a file whose sequence is indented`() {
        val rendered =
            """
            vote-menu:
              title: Vote
              sites:
              - id: minecraftservers
                slot: 20
                cooldown: 12h
              - id: topg
                slot: 23
                cooldown: 12h
              buttons:
                close: 40
            """.trimIndent()

        val merged = ConfigMerge.appendMissing(onDisk, rendered)

        // It parses at all, which is the whole point.
        val decoded = yaml.decodeFromString(Root.serializer(), merged)

        assertEquals(2, decoded.voteMenu.sites.size, "the owner's sites must survive, exactly once")
        assertEquals(listOf("minecraftservers", "topg"), decoded.voteMenu.sites.map { it.id })
        assertEquals(mapOf("close" to 40), decoded.voteMenu.buttons, "the new key is what we were adding")
        assertEquals(1, Regex("""id: minecraftservers""").findAll(merged).count(), "the list must not be duplicated")
    }

    @Test
    fun `a field added to a sequence entry does not backfill, and does not corrupt`() {
        // `sites.cooldown` is not a path -- it is a field on every entry. There is no correct place
        // to insert one `cooldown:` into an existing list, so the sequence is left alone and the
        // data class default supplies the value on decode.
        val rendered =
            """
            vote-menu:
              title: Vote
              sites:
              - id: minecraftservers
                slot: 20
                cooldown: 12h
            """.trimIndent()

        val merged = ConfigMerge.appendMissing(onDisk, rendered)
        val decoded = yaml.decodeFromString(Root.serializer(), merged)

        assertEquals(2, decoded.voteMenu.sites.size)
        assertTrue(decoded.voteMenu.sites.all { it.cooldown == "" }, "the default applies; nothing is invented")
        assertTrue("cooldown" !in merged, "a field cannot be appended into entries that already exist")
    }

    @Test
    fun `a sequence written at its parent's indent is still recognised as present`() {
        // The same file both ways round: if the merger cannot see that this list exists, it appends
        // a second one, which is the bug.
        val flat =
            """
            vote-menu:
              title: Vote
              sites:
              - id: minecraftservers
                slot: 20
            """.trimIndent()

        val merged = ConfigMerge.appendMissing(flat, flat)

        // Returned verbatim, not re-emitted: when nothing is missing the file is not touched at all.
        assertEquals(flat, merged, "nothing is missing, so nothing may be added")
    }

    @Test
    fun `a key added after a sequence lands outside it`() {
        val rendered =
            """
            vote-menu:
              title: Vote
              sites:
              - id: minecraftservers
                slot: 20
              buttons:
                close: 40
            """.trimIndent()

        val merged = ConfigMerge.appendMissing(onDisk, rendered)

        val buttonsLine = merged.lines().indexOfFirst { it.trimStart().startsWith("buttons:") }
        val lastSite = merged.lines().indexOfLast { it.trimStart().startsWith("- id:") }
        assertTrue(buttonsLine > lastSite, "buttons must follow the list, not land inside it")
        // Indented as a child of vote-menu, not as a sibling of the sequence entries.
        assertEquals("  buttons:", merged.lines()[buttonsLine])
    }
}
