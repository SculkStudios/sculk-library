package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A merge must not separate a key from the `@Comment` lines written above it.
 *
 * `insert` places a new block at `endOfSection(lines, parent)`, and `endOfSection` walks to the first
 * line at or above the parent's indent. Comment lines cannot match `KEY_LINE` — `#` is excluded by
 * the character class — so they are skipped, and the index returned is the *key* line that follows
 * them. The new block therefore lands between that key and its own comments:
 *
 *     # The port to listen on.     <- stranded above someone else's block
 *
 *       # How many votes a minute.
 *       rate-limit: 20
 *     port: 8192                   <- now undocumented
 *
 * Every release that adds a nested key inserts at the same place, so the stray comments pile up
 * above one key and the keys below them lose their documentation. On a file that has been through a
 * few upgrades it reads as the comments being duplicated.
 *
 * Only nested keys are affected: a top-level key has no parent section, so `insert` appends it to
 * the end of the file where there is nothing to split.
 */
class ConfigMergeCommentsTest {
    private val onDisk =
        """
        # What this plugin listens on.

        security:
          # Only accept votes from a configured site.
          require-configured-site: true

        # The port to listen on.
        port: 8192
        """.trimIndent()

    /** The same file from a newer release, which added `security.rate-limit`. */
    private val rendered =
        """
        # What this plugin listens on.

        security:
          # Only accept votes from a configured site.
          require-configured-site: true

          # How many votes a minute one player may send.
          rate-limit: 20

        # The port to listen on.
        port: 8192
        """.trimIndent()

    private fun lineBefore(text: String, key: String): String {
        val lines = text.lines()
        val at = lines.indexOfFirst { it.trimStart().startsWith(key) }
        check(at > 0) { "'$key' not found below the first line of:\n$text" }
        return lines[at - 1].trim()
    }

    @Test
    fun `an inserted key does not steal the comment of the key it is inserted before`() {
        val merged = ConfigMerge.appendMissing(onDisk, rendered)

        assertEquals(
            "# The port to listen on.",
            lineBefore(merged, "port:"),
            "the merge inserted a block between `port` and its own comment:\n$merged",
        )
        assertEquals(
            "# How many votes a minute one player may send.",
            lineBefore(merged, "rate-limit:"),
            "the new key must arrive with its own comment and no other:\n$merged",
        )
    }

    @Test
    fun `comments do not accumulate over repeated upgrades`() {
        // Two releases in a row, each adding one nested key -- which is the ordinary case, not an
        // exotic one. Each insert targets the same position, so a stray-comment bug compounds.
        val second =
            rendered.replace(
                "\n# The port to listen on.",
                "\n  # Reject votes with a blank name.\n  require-name: true\n\n# The port to listen on.",
            )

        val once = ConfigMerge.appendMissing(onDisk, rendered)
        val twice = ConfigMerge.appendMissing(once, second)

        assertEquals(
            1,
            Regex("# The port to listen on\\.").findAll(twice).count(),
            "the comment was copied rather than moved:\n$twice",
        )
        assertEquals(
            "# The port to listen on.",
            lineBefore(twice, "port:"),
            "after two upgrades `port` no longer carries its own comment:\n$twice",
        )
    }
}
