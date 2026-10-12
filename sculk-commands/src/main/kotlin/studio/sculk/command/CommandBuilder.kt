package studio.sculk.command

import studio.sculk.annotation.SculkStable
import kotlin.time.Duration

/** Marks the command DSL so an inner block cannot reach an outer builder by accident. */
@SculkStable
@DslMarker
public annotation class CommandDsl

/**
 * Declares a command. Arguments declared in a block form a chain, each following the last;
 * [CommandBuilder.literal] branches from the end of the chain, and [CommandBuilder.executes] runs
 * when input ends there.
 *
 * ```kotlin
 * val pay = command("pay") {
 *     permission = "economy.pay"
 *     val target = word("player")
 *     val amount = double("amount", min = 0.01)
 *     player {
 *         reply("<success>Sent <amount> to <target>.", "amount" to this[amount], "target" to this[target])
 *     }
 * }
 * ```
 *
 * The tree is checked as it is built: duplicate words, two arguments at one position, a greedy
 * argument with anything after it, or a required argument after an optional one each throw, since
 * each is a wiring bug.
 */
@SculkStable
public fun command(name: String, vararg aliases: String, block: CommandBuilder.() -> Unit): CommandSpec {
    val builder = CommandBuilder(Draft.literal(name), parent = null).apply(block)
    val root = builder.build()
    aliases.forEach(::validWord)
    return CommandSpec(root as CommandNode.Literal, aliases.toList(), builder.description)
}

@SculkStable
@CommandDsl
public class CommandBuilder internal constructor(private val scope: Draft, private val parent: CommandBuilder?) {
    private var tail: Draft = scope
    private val middleware = mutableListOf<CommandMiddleware>()
    private val branches = mutableListOf<Pair<Draft, CommandBuilder>>()

    /** Shown in help. */
    public var description: String? = null

    /** The permission needed to see and use this command or branch. */
    public var permission: String? = null

    private var condition: ((CommandSource) -> Boolean)? = null

    /** An extra condition for this command or branch, such as a game mode. Senders who fail it never see it. */
    public fun requires(condition: (CommandSource) -> Boolean) {
        check(this.condition == null) { "A branch has one requires block; combine the conditions." }
        this.condition = condition
    }

    /** A fixed word branching from the end of the chain; [aliases] lead to the same branch. */
    public fun literal(name: String, vararg aliases: String, block: CommandBuilder.() -> Unit) {
        val node = Draft.literal(name, aliases.toList())
        attach(node)
        branches += node to CommandBuilder(node, this).apply(block)
    }

    /** An argument of any [type], appended to the chain. */
    public fun <T : Any> argument(name: String, type: ArgumentType<T>): ArgumentHandle<T> {
        checkName(name)
        check(tail.argument?.type?.shape != ArgumentShape.GREEDY) { "Nothing can follow the greedy argument <${tail.name}>." }
        val declaration = ArgumentDeclaration(name, type)
        val node = Draft.argument(declaration)
        attach(node)
        tail = node
        return ArgumentHandle(name, declaration)
    }

    public fun boolean(name: String): ArgumentHandle<Boolean> = argument(name, BuiltinArgument.BooleanArgument())

    public fun integer(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): ArgumentHandle<Int> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.IntegerArgument(min, max))
    }

    public fun long(name: String, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): ArgumentHandle<Long> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.LongArgument(min, max))
    }

    public fun double(name: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): ArgumentHandle<Double> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.DoubleArgument(min, max))
    }

    /** One word, up to the next space. */
    public fun word(name: String, maxLength: Int = Int.MAX_VALUE, pattern: Regex? = null): ArgumentHandle<String> =
        argument(name, BuiltinArgument.StringArgument(ArgumentShape.WORD, maxLength, pattern))

    /** A word, or quoted text with spaces. */
    public fun string(name: String, maxLength: Int = Int.MAX_VALUE, pattern: Regex? = null): ArgumentHandle<String> =
        argument(name, BuiltinArgument.StringArgument(ArgumentShape.QUOTABLE, maxLength, pattern))

    /** The rest of the input. Must be the last argument. */
    public fun greedy(name: String, maxLength: Int = Int.MAX_VALUE): ArgumentHandle<String> =
        argument(name, BuiltinArgument.StringArgument(ArgumentShape.GREEDY, maxLength, null))

    /** One of [choices], by key; matching ignores case. */
    public fun <T : Any> choice(name: String, choices: Map<String, T>): ArgumentHandle<T> =
        argument(name, BuiltinArgument.ChoiceArgument(choices))

    public fun uuid(name: String): ArgumentHandle<java.util.UUID> = argument(name, BuiltinArgument.UuidArgument())

    public fun key(name: String): ArgumentHandle<net.kyori.adventure.key.Key> = argument(name, BuiltinArgument.KeyArgument())

    public fun duration(name: String, min: Duration = Duration.ZERO, max: Duration = Duration.INFINITE): ArgumentHandle<Duration> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.DurationArgument(min, max))
    }

    /** Makes this argument optional; it reads as null when left out. Only the last arguments of a chain can be optional. */
    public fun <T : Any> ArgumentHandle<T>.optional(): ArgumentHandle<T?> {
        declaration.optional = true
        @Suppress("UNCHECKED_CAST")
        return this as ArgumentHandle<T?>
    }

    /** Makes this argument optional; it reads as [default] when left out. */
    public fun <T : Any> ArgumentHandle<T>.optional(default: T): ArgumentHandle<T> {
        declaration.optional = true
        declaration.default = default
        return this
    }

    /** Replaces this argument's completions, for values that depend on the server or the sender. */
    public fun <T> ArgumentHandle<T>.suggests(provider: SuggestionProvider): ArgumentHandle<T> {
        declaration.suggestions = provider
        return this
    }

    /** Wraps every executor in this block and the blocks inside it, outermost first. */
    public fun use(middleware: CommandMiddleware) {
        this.middleware += middleware
    }

    /** Lets each sender run the executors in this block once per [duration]. */
    public fun cooldown(duration: Duration) {
        use(Cooldown(duration))
    }

    /** Asks for the command to be run twice within [window] before it proceeds. */
    public fun confirm(window: Duration) {
        use(Confirmation(window))
    }

    /** Runs [body] when input ends at the end of the chain. */
    public fun executes(body: suspend CommandContext.() -> Unit) {
        setExecutor(requiresPlayer = false, body)
    }

    /** Like [executes], for players only; anyone else gets the players-only message. */
    public fun player(body: suspend CommandContext.() -> Unit) {
        setExecutor(requiresPlayer = true, body)
    }

    private fun setExecutor(requiresPlayer: Boolean, body: suspend CommandContext.() -> Unit) {
        check(tail.executor == null) { "<${tail.name}> already has an executor." }
        tail.executor = PendingExecutor(this, requiresPlayer, body)
    }

    private fun attach(node: Draft) {
        val clash = tail.children.firstOrNull { it.clashesWith(node) }
        check(clash == null) {
            if (node.argument != null) {
                "<${node.name}> and <${clash?.name}> would both read the same position after ${tail.name}."
            } else {
                "'${node.name}' is declared twice after ${tail.name}."
            }
        }
        tail.children += node
    }

    private fun checkName(name: String) {
        require(ARGUMENT_NAME.matches(name)) { "Argument name '$name' must be lower-case letters, digits, '-' or '_'." }
        var builder: CommandBuilder? = this
        while (builder != null) {
            check(!builder.scope.chainHas(name)) { "Two arguments on one path are both named <$name>." }
            builder = builder.parent
        }
    }

    internal fun middlewareChain(): List<CommandMiddleware> = parent?.middlewareChain().orEmpty() + middleware

    internal fun build(): CommandNode {
        applyRequirements()
        return scope.freeze()
    }

    private fun applyRequirements() {
        scope.requirement = Requirement(permission, condition)
        branches.forEach { (_, builder) -> builder.applyRequirements() }
    }

    private companion object {
        val ARGUMENT_NAME = Regex("[a-z][a-z0-9_-]*")
    }
}

internal class PendingExecutor(val builder: CommandBuilder, val requiresPlayer: Boolean, val body: suspend CommandContext.() -> Unit)

/** A mutable node while the DSL runs; frozen into [CommandNode] once the whole command is declared. */
internal class Draft private constructor(val name: String, val aliases: List<String>, val argument: ArgumentDeclaration?) {
    val children = mutableListOf<Draft>()
    var executor: PendingExecutor? = null
    var requirement: Requirement = Requirement.NONE

    fun clashesWith(other: Draft): Boolean = when {
        argument != null && other.argument != null -> true

        argument == null && other.argument == null -> (aliases + name).any { word ->
            (other.aliases + other.name).any { it.equals(word, true) }
        }

        else -> false
    }

    fun chainHas(argumentName: String): Boolean =
        argument?.name == argumentName || children.any { it.argument != null && it.chainHas(argumentName) }

    fun freeze(): CommandNode {
        checkOptionalChain()
        val frozenChildren = children.flatMap { it.freezeWithAliases() }
        val runs = executor ?: inheritedFromOptionalChild()
        val compiled = runs?.let { CommandExecutor(it.builder.middlewareChain(), it.requiresPlayer, it.body) }
        return if (argument == null) {
            CommandNode.Literal(name, frozenChildren, compiled, requirement)
        } else {
            CommandNode.Argument(
                ArgumentHandle<Any?>(argument.name, argument),
                argument.type,
                argument.suggestions,
                frozenChildren,
                compiled,
                requirement,
            )
        }
    }

    private fun freezeWithAliases(): List<CommandNode> {
        val frozen = freeze()
        if (argument != null || aliases.isEmpty()) return listOf(frozen)
        val literal = frozen as CommandNode.Literal
        return listOf(frozen) + aliases.map { CommandNode.Literal(it, literal.children, literal.executor, literal.requirement) }
    }

    /** A node runs the executor of an optional argument after it, as if the sender stopped here. */
    private fun inheritedFromOptionalChild(): PendingExecutor? {
        val next = children.singleOrNull { it.argument != null } ?: return null
        if (next.argument?.optional != true) return null
        return next.executor ?: next.inheritedFromOptionalChild()
    }

    private fun checkOptionalChain() {
        if (argument?.optional != true) return
        val required = children.firstOrNull { it.argument != null && !it.argument.optional }
        check(required == null) {
            "<${required?.name}> is required but follows the optional <$name>; only the last arguments can be optional."
        }
        check(children.none { it.argument == null }) { "A word cannot follow the optional <$name>." }
    }

    companion object {
        fun literal(name: String, aliases: List<String> = emptyList()): Draft {
            validWord(name)
            aliases.forEach(::validWord)
            return Draft(name, aliases, null)
        }

        fun argument(declaration: ArgumentDeclaration): Draft = Draft(declaration.name, emptyList(), declaration)
    }
}

internal fun validWord(word: String) {
    require(word.isNotEmpty() && word.none { it.isWhitespace() } && word == word.lowercase()) {
        "Command word '$word' must be lower case with no spaces."
    }
}
