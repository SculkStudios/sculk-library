package gg.sculk.core.command.argument

import gg.sculk.core.annotation.SculkInternal
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Internal contract for parsing a raw string token into a typed value.
 *
 * This type is intentionally internal. Public APIs never expose generics.
 * Argument parsers are registered by name on [CommandNode] and resolved
 * during execution.
 */
@SculkInternal
public interface ArgumentParser<T> {
    /** Human-readable name used in tab completion and error messages. */
    public val typeName: String

    /**
     * Parses [input] into [T], returning null if the input is invalid.
     * A null return causes an automatic "invalid argument" error reply.
     */
    public fun parse(input: String): T?

    /** Returns tab completion suggestions for the current [input]. */
    public fun suggest(input: String): List<String> = emptyList()
}

// ---------------------------------------------------------------------------
// Built-in parsers
// ---------------------------------------------------------------------------

@SculkInternal
public object StringParser : ArgumentParser<String> {
    override val typeName: String = "text"

    override fun parse(input: String): String = input
}

@SculkInternal
public object IntParser : ArgumentParser<Int> {
    override val typeName: String = "number"

    override fun parse(input: String): Int? = input.toIntOrNull()
}

@SculkInternal
public object DoubleParser : ArgumentParser<Double> {
    override val typeName: String = "decimal"

    override fun parse(input: String): Double? = input.toDoubleOrNull()
}

@SculkInternal
public object BooleanParser : ArgumentParser<Boolean> {
    override val typeName: String = "true|false"

    override fun parse(input: String): Boolean? =
        when (input.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }

    override fun suggest(input: String): List<String> = listOf("true", "false")
}

@SculkInternal
public object PlayerParser : ArgumentParser<Player> {
    override val typeName: String = "player"

    override fun parse(input: String): Player? = Bukkit.getPlayerExact(input)

    override fun suggest(input: String): List<String> =
        Bukkit
            .getOnlinePlayers()
            .map { it.name }
            .filter { it.startsWith(input, ignoreCase = true) }
}

@SculkInternal
public class ChoiceParser(
    private val choices: List<String>,
) : ArgumentParser<String> {
    override val typeName: String = choices.joinToString("|")

    override fun parse(input: String): String? = choices.firstOrNull { it.equals(input, ignoreCase = true) }

    override fun suggest(input: String): List<String> = choices.filter { it.startsWith(input, ignoreCase = true) }
}

/**
 * Registry of named [ArgumentParser] instances, resolved by Kotlin type at DSL time.
 * Kept internal — plugin code never references parsers directly.
 */
@SculkInternal
public object ArgumentParsers {
    private val parsers: MutableMap<String, ArgumentParser<*>> =
        mutableMapOf(
            "String" to StringParser,
            "Int" to IntParser,
            "Double" to DoubleParser,
            "Boolean" to BooleanParser,
            "Player" to PlayerParser,
        )

    public fun register(
        typeName: String,
        parser: ArgumentParser<*>,
    ) {
        parsers[typeName] = parser
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T> get(typeName: String): ArgumentParser<T>? = parsers[typeName] as? ArgumentParser<T>
}
