package studio.sculk.command

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * The flags after a command's arguments, such as `--silent -a 5`, read as one trailing argument:
 * Brigadier has no flag nodes, and every ordering of several flags would otherwise need its own
 * branch. Platforms carry it as greedy text and parse it with these rules, so the interpreter and
 * the compiled trees agree.
 *
 * A presence flag is written `--name` or `-n`; short presence flags combine as `-ab`. A value flag
 * takes the next word: `--amount 5` or `-a 5`. Each flag may appear once; anything else is invalid.
 */
@SculkStable
public class FlagsArgument internal constructor() : ArgumentType<FlagValues> {
    private val definitions = mutableListOf<FlagDefinition>()

    /** The declared flags, in declaration order. */
    public val flags: List<FlagDefinition> get() = definitions.toList()

    internal fun add(definition: FlagDefinition) {
        check(definitions.none { it.name == definition.name }) { "--${definition.name} is declared twice." }
        definition.short?.let { short -> check(definitions.none { it.short == short }) { "-$short is declared twice." } }
        definitions += definition
    }

    override val shape: ArgumentShape get() = ArgumentShape.GREEDY

    override val expected: String
        get() = "flags: " + definitions.joinToString { flag -> "--${flag.name}" + (flag.type?.let { " <value>" }.orEmpty()) }

    override fun parse(text: String, source: CommandSource): SculkResult<FlagValues> {
        val tokens = text.split(' ').filter(String::isNotEmpty)
        val values = LinkedHashMap<String, Any>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index++]
            val named = when {
                token.startsWith("--") -> listOfNotNull(definitions.firstOrNull { it.name == token.removePrefix("--") })
                    .ifEmpty { return invalid(token) }

                token.startsWith("-") && token.length > 1 -> token.drop(1).map { short ->
                    definitions.firstOrNull { it.short == short } ?: return invalid(token)
                }

                else -> return invalid(token)
            }
            for (flag in named) {
                if (flag.name in values) return invalid(token)
                val type = flag.type
                if (type == null) {
                    values[flag.name] = true
                    continue
                }
                if (named.size > 1 || index >= tokens.size) return invalid(token)
                val parsed = type.parse(tokens[index++], source)
                values[flag.name] = when (parsed) {
                    is SculkResult.Success -> parsed.value

                    is SculkFailure -> return SculkFailure.InvalidInput(
                        "argument",
                        "--${flag.name} ${type.expected}",
                        "'${tokens[index - 1]}'",
                    )
                }
            }
        }
        return SculkResult.success(FlagValues(values))
    }

    /** Completes the last word: an unused flag, or a value for the flag before it. */
    override fun suggest(partial: String, source: CommandSource): List<String> {
        val prefix = partial.substring(0, partial.lastIndexOf(' ') + 1)
        val words = prefix.split(' ').filter(String::isNotEmpty)
        val last = partial.substring(prefix.length)
        val previous = words.lastOrNull()?.let { word -> definitions.firstOrNull { "--${it.name}" == word || "-${it.short}" == word } }
        val candidates = previous?.type?.suggest(last, source)
            ?: definitions.filter { flag -> "--${flag.name}" !in words && "-${flag.short}" !in words }.map { "--${it.name}" }
        return candidates.filter { it.startsWith(last) }.map { prefix + it }
    }

    private fun invalid(token: String) = SculkFailure.InvalidInput("argument", expected, "'$token'")
}

/** One declared flag; a null [type] makes it a presence flag. */
@SculkStable
public class FlagDefinition internal constructor(public val name: String, public val short: Char?, public val type: ArgumentType<*>?)

/** The flags a sender wrote, by name. */
@SculkStable
public class FlagValues internal constructor(private val values: Map<String, Any>) {
    public operator fun contains(flag: String): Boolean = flag in values

    public fun value(flag: String): Any? = values[flag]

    override fun toString(): String = values.toString()

    @SculkStable
    public companion object {
        public val NONE: FlagValues = FlagValues(emptyMap())
    }
}
