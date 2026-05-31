package studio.sculk.command.argument

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.time.Duration
import java.util.UUID

/**
 * Contract for parsing a raw string token into a typed value.
 *
 * Implement this interface to add a custom argument type and register it with
 * [CommandBuilder.argument][studio.sculk.command.CommandBuilder.argument].
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
public class BoundedLongParser(
    private val min: Long?,
    private val max: Long?,
) : ArgumentParser<Long> {
    override val typeName: String = "number"

    override fun parse(input: String): Long? =
        input.toLongOrNull()?.takeIf { value ->
            (min == null || value >= min) && (max == null || value <= max)
        }
}

@SculkInternal
public object IntParser : ArgumentParser<Int> {
    override val typeName: String = "number"

    override fun parse(input: String): Int? = input.toIntOrNull()
}

@SculkInternal
public class BoundedIntParser(
    private val min: Int?,
    private val max: Int?,
) : ArgumentParser<Int> {
    override val typeName: String = "number"

    override fun parse(input: String): Int? =
        input.toIntOrNull()?.takeIf { value ->
            (min == null || value >= min) && (max == null || value <= max)
        }
}

@SculkInternal
public object DoubleParser : ArgumentParser<Double> {
    override val typeName: String = "decimal"

    override fun parse(input: String): Double? = input.toDoubleOrNull()
}

@SculkInternal
public class BoundedDoubleParser(
    private val min: Double?,
    private val max: Double?,
) : ArgumentParser<Double> {
    override val typeName: String = "decimal"

    override fun parse(input: String): Double? =
        input.toDoubleOrNull()?.takeIf { value ->
            (min == null || value >= min) && (max == null || value <= max)
        }
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
public object UuidParser : ArgumentParser<UUID> {
    override val typeName: String = "uuid"

    override fun parse(input: String): UUID? = runCatching { UUID.fromString(input) }.getOrNull()
}

@SculkInternal
public object WorldParser : ArgumentParser<World> {
    override val typeName: String = "world"

    override fun parse(input: String): World? = Bukkit.getWorld(input)

    override fun suggest(input: String): List<String> =
        Bukkit.getWorlds().map { it.name }.filter { it.startsWith(input, ignoreCase = true) }
}

@SculkInternal
public object MaterialParser : ArgumentParser<Material> {
    override val typeName: String = "material"

    override fun parse(input: String): Material? =
        Material.matchMaterial(input.uppercase())
            ?: Material.matchMaterial(input)
            ?: Material.matchMaterial(input.replace('-', '_').uppercase())

    override fun suggest(input: String): List<String> =
        Material.entries
            .asSequence()
            .map { it.name.lowercase() }
            .filter { it.startsWith(input.lowercase()) }
            .take(50)
            .toList()
}

@SculkInternal
public object DurationParser : ArgumentParser<Duration> {
    override val typeName: String = "duration"

    override fun parse(input: String): Duration? {
        val trimmed = input.trim().lowercase()
        val number = trimmed.dropLast(1).toLongOrNull() ?: return null
        return when (trimmed.lastOrNull()) {
            't' -> Duration.ofMillis(number * 50L)
            's' -> Duration.ofSeconds(number)
            'm' -> Duration.ofMinutes(number)
            'h' -> Duration.ofHours(number)
            'd' -> Duration.ofDays(number)
            else -> null
        }
    }

    override fun suggest(input: String): List<String> = listOf("10s", "1m", "5m", "1h").filter { it.startsWith(input) }
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
