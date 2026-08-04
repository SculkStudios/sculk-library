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

// Built-in parsers

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
public class BoundedLongParser(private val min: Long?, private val max: Long?) : ArgumentParser<Long> {
    override val typeName: String = "number"

    override fun parse(input: String): Long? = input.toLongOrNull()?.takeIf { value ->
        (min == null || value >= min) && (max == null || value <= max)
    }
}

@SculkInternal
public object IntParser : ArgumentParser<Int> {
    override val typeName: String = "number"

    override fun parse(input: String): Int? = input.toIntOrNull()
}

@SculkInternal
public class BoundedIntParser(private val min: Int?, private val max: Int?) : ArgumentParser<Int> {
    override val typeName: String = "number"

    override fun parse(input: String): Int? = input.toIntOrNull()?.takeIf { value ->
        (min == null || value >= min) && (max == null || value <= max)
    }
}

@SculkInternal
public object DoubleParser : ArgumentParser<Double> {
    override val typeName: String = "decimal"

    override fun parse(input: String): Double? = input.toDoubleOrNull()
}

@SculkInternal
public class BoundedDoubleParser(private val min: Double?, private val max: Double?) : ArgumentParser<Double> {
    override val typeName: String = "decimal"

    override fun parse(input: String): Double? = input.toDoubleOrNull()?.takeIf { value ->
        (min == null || value >= min) && (max == null || value <= max)
    }
}

@SculkInternal
public object BooleanParser : ArgumentParser<Boolean> {
    override val typeName: String = "true|false"

    override fun parse(input: String): Boolean? = when (input.lowercase()) {
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

    override fun suggest(input: String): List<String> = Bukkit
        .getOnlinePlayers()
        .map { it.name }
        .filter { it.startsWith(input, ignoreCase = true) }
}

/**
 * A player name that does not have to belong to anyone online.
 *
 * [PlayerParser] resolves to a live [Player] and therefore rejects everything else, which makes it
 * the wrong type for a whole class of command: banning, unbanning, looking up history and checking
 * alts are all things you do to somebody who has already left. Reaching for `string` instead is the
 * obvious workaround and it is a bad one -- [StringParser] offers no completions, so the command
 * silently loses tab completion and the operator is left typing names by hand and guessing at
 * capitalisation.
 *
 * This keeps the completions and drops the requirement. Suggestions come from online players first,
 * then names the server has seen before, so the common case still completes in one keystroke while
 * an offline target remains typeable.
 */
@SculkStable
public object PlayerNameParser : ArgumentParser<String> {
    override val typeName: String = "player"

    /**
     * Accepts any plausible name.
     *
     * Deliberately not a strict `[A-Za-z0-9_]{3,16}` check: Bedrock players arrive through Geyser
     * with a prefix and a space, and cracked servers allow names the Mojang rules never did.
     * Rejecting them here would refuse to ban a player the server is perfectly happy to host. The
     * bound that matters is length, because the name ends up in a database column.
     */
    override fun parse(input: String): String? = input.takeIf { it.isNotBlank() && it.length <= MAX_NAME_LENGTH }

    override fun suggest(input: String): List<String> {
        val online = Bukkit.getOnlinePlayers().map { it.name }
        // Online first and de-duplicated, so somebody standing in front of you is never buried
        // under a page of names from the usercache.
        val known = Bukkit.getOfflinePlayers().mapNotNull { it.name }
        return (online + known)
            .distinct()
            .filter { it.startsWith(input, ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
    }

    /** Vanilla's limit; a longer name cannot have been used to log in. */
    private const val MAX_NAME_LENGTH = 16

    /**
     * Brigadier sends the whole list to the client on every keystroke, and a long-lived server's
     * usercache holds tens of thousands of names.
     */
    private const val MAX_SUGGESTIONS = 50
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

    override fun parse(input: String): Material? = Material.matchMaterial(input.uppercase())
        ?: Material.matchMaterial(input)
        ?: Material.matchMaterial(input.replace('-', '_').uppercase())

    override fun suggest(input: String): List<String> = Material.entries
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
public class ChoiceParser(override val typeName: String, private val options: () -> Collection<String>) : ArgumentParser<String> {
    override fun parse(input: String): String? = options().firstOrNull { it.equals(input, ignoreCase = true) }

    override fun suggest(input: String): List<String> = options().filter { it.startsWith(input, ignoreCase = true) }
}

/**
 * Accepts one constant of [type], matched case-insensitively on its name.
 *
 * enumValueOf throws on a miss rather than returning null, so the constants are matched against a
 * list instead -- an exception per mistyped argument is a cost paid on the wrong path.
 */
@SculkInternal
public class EnumParser<E : Enum<E>>(private val type: Class<E>) : ArgumentParser<E> {
    private val constants: List<E> = type.enumConstants.toList()

    override val typeName: String = type.simpleName.lowercase()

    override fun parse(input: String): E? = constants.firstOrNull { it.name.equals(input, ignoreCase = true) }

    override fun suggest(input: String): List<String> =
        constants.map { it.name.lowercase() }.filter { it.startsWith(input, ignoreCase = true) }
}
