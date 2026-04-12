package gg.sculk.core.command.argument

import gg.sculk.core.annotation.SculkInternal
import gg.sculk.core.annotation.SculkStable
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Contract for parsing a raw string token into a typed value.
 *
 * Implement this interface to add a custom argument type and register it with
 * [CommandBuilder.argument][gg.sculk.core.command.CommandBuilder.argument].
 *
 * ```kotlin
 * object UuidParser : ArgumentParser<UUID> {
 *     override val typeName = "uuid"
 *     override fun parse(input: String): UUID? = runCatching { UUID.fromString(input) }.getOrNull()
 * }
 *
 * command("find") {
 *     argument("id", UuidParser)
 *     executes {
 *         val id = argument<UUID>("id")
 *         reply("Looking up $id")
 *     }
 * }
 * ```
 */
@SculkStable
public interface ArgumentParser<T> {
    /** Human-readable name used in usage lines and error messages. */
    public val typeName: String

    /**
     * Parses [input] into [T], returning null if the input is invalid.
     * A null return produces an automatic "invalid argument" error reply.
     */
    public fun parse(input: String): T?

    /** Returns tab-completion suggestions for the current [input]. */
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
public object LongParser : ArgumentParser<Long> {
    override val typeName: String = "number"

    override fun parse(input: String): Long? = input.toLongOrNull()
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

/**
 * Greedy string parser — consumes the remainder of the input as a single string.
 *
 * Must be the last argument registered on a command node.
 */
@SculkInternal
public object GreedyStringParser : ArgumentParser<String> {
    override val typeName: String = "text..."

    override fun parse(input: String): String = input
}

@SculkInternal
public class ChoiceParser(
    private val choices: List<String>,
) : ArgumentParser<String> {
    override val typeName: String = choices.joinToString("|")

    override fun parse(input: String): String? = choices.firstOrNull { it.equals(input, ignoreCase = true) }

    override fun suggest(input: String): List<String> = choices.filter { it.startsWith(input, ignoreCase = true) }
}
