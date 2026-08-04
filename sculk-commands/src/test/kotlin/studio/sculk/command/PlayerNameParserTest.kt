package studio.sculk.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import studio.sculk.command.argument.PlayerNameParser
import studio.sculk.command.argument.StringParser

/**
 * The parser that exists so moderation commands keep their tab completion.
 *
 * `PlayerParser` resolves to a live `Player`, so it cannot name somebody who has left, and reaching
 * for `string` instead costs completions without saying so. A plugin made exactly that swap across
 * every one of its punishment commands and shipped with no tab completion anywhere; the operator
 * had to type every name by hand.
 */
class PlayerNameParserTest {
    @Test
    fun `a name is accepted without the player being online`() {
        // The whole point: Bukkit is not running here, so nobody is online by definition.
        assertNotNull(PlayerNameParser.parse("Daisy"))
        assertEquals("Daisy", PlayerNameParser.parse("Daisy"))
    }

    @Test
    fun `a name longer than a login name is rejected`() {
        // It ends up in a database column, and no such account can have played here.
        assertNull(PlayerNameParser.parse("a".repeat(17)))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(PlayerNameParser.parse(""))
        assertNull(PlayerNameParser.parse("   "))
    }

    @Test
    fun `a Bedrock style name is accepted rather than refused`() {
        // Geyser prefixes names, and a strict [A-Za-z0-9_] rule would refuse to ban a player the
        // server is perfectly happy to host.
        assertEquals(".Daisy", PlayerNameParser.parse(".Daisy"))
    }

    @Test
    fun `it advertises itself as a player argument`() {
        assertEquals("player", PlayerNameParser.typeName)
    }

    @Test
    fun `unlike a plain string it offers completions`() {
        // StringParser's empty suggest() is the defect being designed out: a command using it
        // loses tab completion silently, with nothing at the call site to show for it.
        assertEquals(emptyList<String>(), StringParser.suggest("Dai"))
    }
}
